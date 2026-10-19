package com.nasa.agent;


import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;


import java.util.List;


public class NasaKnowledgeRetrieval {


    private static final int MAX_RESULTS = 5;
    private static final double MIN_SCORE = 0.70;


    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;


    // Injected dependencies
    public NasaKnowledgeRetrieval(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }


    public String search(String query) {
        if (query == null || query.isBlank()) {
            return "NASA knowledge query is empty.";
        }
        String normalizedQuery = query.trim();


        try {
            System.out.println("[DEBUG] Generating embedding for query: \"" + normalizedQuery + "\"");
            Embedding queryEmbedding = embeddingModel.embed(normalizedQuery).content();


            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(MAX_RESULTS)
                    .minScore(MIN_SCORE)
                    .build();


            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();


            if (matches.isEmpty()) {
                return "No sufficiently relevant NASA documentation was found for the query.";
            }


            return formatResults(normalizedQuery, matches);


        } catch (Exception e) {
            return "NASA_KNOWLEDGE_RETRIEVAL_ERROR: " + e.getMessage();
        }
    }


    private String formatResults(String query, List<EmbeddingMatch<TextSegment>> matches) {
        StringBuilder result = new StringBuilder();
        result.append("NASA knowledge retrieval results for query: \"").append(query).append("\"\n\n");


        int rank = 1;
        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment segment = match.embedded();
            Metadata metadata = segment.metadata();


            result.append("SOURCE ").append(rank).append("\n");
            result.append("Title: ").append(meta(metadata, "title")).append("\n");
            result.append("URL: ").append(meta(metadata, "source_url")).append("\n");
            result.append("Document ID: ").append(meta(metadata, "document_id")).append("\n");
            result.append("Content hash: ").append(meta(metadata, "content_hash")).append("\n");
            result.append("Chunk: ").append(meta(metadata, "chunk_index")).append("\n");
            result.append(String.format("Relevance score: %.4f%n", match.score()));
            result.append("Content:\n").append(segment.text()).append("\n\n");
            rank++;
        }
        return result.toString().trim();
    }


    /**
     * Reads a metadata value as text regardless of its stored type. "chunk_index" and "chunk_count"
     * are written as integers during ingestion, and Metadata.getString() rejects non-String values.
     */
    private static String meta(Metadata metadata, String key) {
        if (metadata == null) return "";
        Object value = metadata.toMap().get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
