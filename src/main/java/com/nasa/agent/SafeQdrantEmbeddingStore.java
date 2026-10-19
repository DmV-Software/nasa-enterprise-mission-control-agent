package com.nasa.agent;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import dev.langchain4j.store.embedding.filter.Filter;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wrapper around langchain4j's {@code QdrantEmbeddingStore} that fixes {@code search()}.
 *
 * <p><b>Why it exists.</b> langchain4j-qdrant 0.36.0 asks Qdrant to return the stored vector of every
 * hit ({@code with_vectors = true}) and then recomputes cosine similarity locally against the query
 * embedding. It reads the vector from the legacy {@code Vector.data} field of the gRPC response.
 * Current Qdrant servers (1.10+ / Qdrant Cloud) no longer fill that field, so the stored vector comes
 * back with length 0 and the search fails with:
 * {@code Length of vector a (0) must be equal to the length of vector b (768)}.
 *
 * <p><b>What it does.</b> Writes (add / addAll / removeAll) are delegated unchanged to the original
 * store, so ingestion keeps working exactly as before. {@code search()} is executed directly through
 * the Qdrant gRPC client WITHOUT requesting vectors, and uses the similarity score that Qdrant itself
 * returns (cosine distance => score is the cosine similarity). The score is then mapped with
 * {@link RelevanceScore#fromCosineSimilarity(double)} - the same 0..1 scale langchain4j used before -
 * so {@code MIN_SCORE} in {@link NasaKnowledgeRetrieval} keeps its meaning.
 */
public class SafeQdrantEmbeddingStore implements EmbeddingStore<TextSegment> {

    /** Payload key under which langchain4j stores the segment text. */
    private static final String PAYLOAD_TEXT_SEGMENT_KEY = "text_segment";

    private final EmbeddingStore<TextSegment> delegate;
    private final QdrantClient client;
    private final String collectionName;

    public SafeQdrantEmbeddingStore(EmbeddingStore<TextSegment> delegate, QdrantClient client, String collectionName) {
        this.delegate = delegate;
        this.client = client;
        this.collectionName = collectionName;
    }

    // ------------------------------------------------------------------ search (fixed)

    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        if (request.filter() != null) {
            throw new UnsupportedOperationException("Metadata filters are not supported by SafeQdrantEmbeddingStore.search().");
        }
        try {
            SearchPoints searchPoints = SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(request.queryEmbedding().vectorAsList())
                    .setLimit(request.maxResults())
                    .setWithPayload(WithPayloadSelectorFactory.enable(true))
                    // Deliberately NO setWithVectors(...): we use Qdrant's own score.
                    .build();

            List<ScoredPoint> points = client.searchAsync(searchPoints).get();

            List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
            for (ScoredPoint point : points) {
                EmbeddingMatch<TextSegment> match = toMatch(point);
                if (match != null && match.score() >= request.minScore()) {
                    matches.add(match);
                }
            }
            return new EmbeddingSearchResult<>(matches);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant search interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Qdrant search failed for collection '" + collectionName + "': " + e.getMessage(), e);
        }
    }

    private static EmbeddingMatch<TextSegment> toMatch(ScoredPoint point) {
        Map<String, JsonWithInt.Value> payload = point.getPayloadMap();

        JsonWithInt.Value textValue = payload.get(PAYLOAD_TEXT_SEGMENT_KEY);
        if (textValue == null || textValue.getKindCase() != JsonWithInt.Value.KindCase.STRING_VALUE) {
            return null; // not a text segment written by langchain4j
        }

        Metadata metadata = new Metadata();
        for (Map.Entry<String, JsonWithInt.Value> entry : payload.entrySet()) {
            if (PAYLOAD_TEXT_SEGMENT_KEY.equals(entry.getKey())) continue;
            Object converted = convert(entry.getValue());
            if (converted instanceof String) {
                metadata.put(entry.getKey(), (String) converted);
            } else if (converted instanceof Long) {
                metadata.put(entry.getKey(), ((Long) converted).longValue());
            } else if (converted instanceof Double) {
                metadata.put(entry.getKey(), ((Double) converted).doubleValue());
            }
        }

        TextSegment segment = TextSegment.from(textValue.getStringValue(), metadata);
        String id = point.getId().hasUuid() ? point.getId().getUuid() : String.valueOf(point.getId().getNum());
        double relevance = RelevanceScore.fromCosineSimilarity(point.getScore());

        return new EmbeddingMatch<>(relevance, id, (Embedding) null, segment);
    }

    /** Converts a Qdrant payload value into a type accepted by langchain4j {@link Metadata}. */
    private static Object convert(JsonWithInt.Value value) {
        switch (value.getKindCase()) {
            case STRING_VALUE:
                return value.getStringValue();
            case INTEGER_VALUE:
                return value.getIntegerValue();   // long
            case DOUBLE_VALUE:
                return value.getDoubleValue();
            case BOOL_VALUE:
                return String.valueOf(value.getBoolValue());
            default:
                return null; // null / nested struct / list are not used by this project
        }
    }

    // ------------------------------------------------------------------ writes (delegated)

    public String add(Embedding embedding) {
        return delegate.add(embedding);
    }

    public void add(String id, Embedding embedding) {
        delegate.add(id, embedding);
    }

    public String add(Embedding embedding, TextSegment textSegment) {
        return delegate.add(embedding, textSegment);
    }

    public List<String> addAll(List<Embedding> embeddings) {
        return delegate.addAll(embeddings);
    }

    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        return delegate.addAll(embeddings, embedded);
    }

    public void removeAll(Filter filter) {
        delegate.removeAll(filter);
    }
}
