package com.micro1.agent;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;

import java.util.List;

public class NasaKnowledgeRetrieval {

    private static final String COLLECTION_NAME =
            "nasa_knowledge";

    private static final int EMBEDDING_DIMENSION =
            768;

    /*
     * LangChain4j 0.35.0 / Google AI embedding model.
     */
    private static final String EMBEDDING_MODEL =
            "embedding-001";

    private static final int MAX_RESULTS =
            5;

    private static final double MIN_SCORE =
            0.70;

    private final EmbeddingModel embeddingModel;

    private final EmbeddingStore<TextSegment>
            embeddingStore;

    public NasaKnowledgeRetrieval() {

        String apiKey =
                resolveGeminiApiKey();

        String qdrantHost =
                envOrDefault(
                        "QDRANT_HOST",
                        "localhost"
                );

        int qdrantPort =
                Integer.parseInt(
                        envOrDefault(
                                "QDRANT_PORT",
                                "6334"
                        )
                );

        this.embeddingModel =
                GoogleAiEmbeddingModel.builder()
                        .apiKey(apiKey)
                        .modelName(EMBEDDING_MODEL)
                        .taskType(
                                GoogleAiEmbeddingModel.TaskType
                                        .RETRIEVAL_QUERY
                        )
                        .outputDimensionality(
                                EMBEDDING_DIMENSION
                        )
                        .maxRetries(3)
                        .build();

        this.embeddingStore =
                QdrantEmbeddingStore.builder()
                        .host(qdrantHost)
                        .port(qdrantPort)
                        .collectionName(
                                COLLECTION_NAME
                        )
                        .build();
    }

    public String search(String query) {

        if (query == null
                || query.isBlank()) {

            return "NASA knowledge query is empty.";
        }

        Embedding queryEmbedding =
                embeddingModel
                        .embed(query)
                        .content();

        EmbeddingSearchRequest request =
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(MAX_RESULTS)
                        .minScore(MIN_SCORE)
                        .build();

        List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore
                        .search(request)
                        .matches();

        if (matches.isEmpty()) {

            return "No relevant NASA knowledge was found "
                    + "for the query.";
        }

        StringBuilder result =
                new StringBuilder();

        result.append(
                "NASA knowledge search results:\n\n"
        );

        int index = 1;

        for (EmbeddingMatch<TextSegment> match
                : matches) {

            TextSegment segment =
                    match.embedded();

            result.append(
                    String.format(
                            "[%d] relevance=%.4f%n",
                            index++,
                            match.score()
                    )
            );

            result.append(
                    segment.text()
            );

            result.append(
                    "\n\n"
            );
        }

        return result.toString().trim();
    }

    private static String resolveGeminiApiKey() {

        String key =
                System.getenv(
                        "GEMINI_EMBEDDING_API_KEY"
                );

        if (key != null && !key.isBlank()) {
            return key.trim();
        }

        key =
                System.getenv(
                        "GEMINI_API_KEY"
                );

        if (key != null && !key.isBlank()) {
            return key.trim();
        }

        key =
                System.getenv(
                        "GEMINI_API_KEYS"
                );

        if (key != null && !key.isBlank()) {

            String first =
                    key.split(",")[0].trim();

            if (!first.isBlank()) {
                return first;
            }
        }

        throw new IllegalStateException(
                "Gemini API key not found. "
                        + "Set GEMINI_EMBEDDING_API_KEY, "
                        + "GEMINI_API_KEY, or GEMINI_API_KEYS."
        );
    }

    private static String envOrDefault(
            String name,
            String defaultValue) {

        String value =
                System.getenv(name);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value.trim();
    }
}