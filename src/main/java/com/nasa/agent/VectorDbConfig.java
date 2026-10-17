package com.nasa.agent;


import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;


public class VectorDbConfig {


    private static final String COLLECTION_NAME = "nasa_knowledge";
    private static final int EMBEDDING_DIMENSION = 768;
    private static final String EMBEDDING_MODEL = "gemini-embedding-001";


    public static EmbeddingStore<TextSegment> createQdrantStore() {
        return QdrantEmbeddingStore.builder()
                .host(requiredEnv("QDRANT_HOST"))
                .port(Integer.parseInt(envOrDefault("QDRANT_PORT", "6334")))
                .useTls(Boolean.parseBoolean(envOrDefault("QDRANT_TLS", "true")))
                .apiKey(requiredEnv("QDRANT_API_KEY"))
                .collectionName(COLLECTION_NAME)
                .build();
    }


    public static EmbeddingModel createRetrievalModel() {
        return buildModel(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_QUERY);
    }


    public static EmbeddingModel createIngestionModel() {
        return buildModel(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_DOCUMENT);
    }


    private static EmbeddingModel buildModel(GoogleAiEmbeddingModel.TaskType taskType) {
        return GoogleAiEmbeddingModel.builder()
                .apiKey(resolveGeminiApiKey())
                .modelName(EMBEDDING_MODEL)
                .taskType(taskType)
                .outputDimensionality(EMBEDDING_DIMENSION)
                .maxRetries(3)
                .build();
    }


    public static String resolveGeminiApiKey() {
        String key = System.getenv("GEMINI_EMBEDDING_API_KEY");
        if (key != null && !key.isBlank()) return key.trim();

        key = System.getenv("GEMINI_API_KEY");
        if (key != null && !key.isBlank()) return key.trim();

        key = System.getenv("GEMINI_API_KEYS");
        if (key != null && !key.isBlank()) {
            String first = key.split(",")[0].trim();
            if (!first.isBlank()) return first;
        }
        throw new IllegalStateException("Gemini API key not found.");
    }


    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable " + name + " is not set.");
        }
        return value.trim();
    }


    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
