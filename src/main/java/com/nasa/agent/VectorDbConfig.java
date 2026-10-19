package com.nasa.agent;


import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;


public class VectorDbConfig {


    private static final String COLLECTION_NAME = "nasa_knowledge";
    private static final int EMBEDDING_DIMENSION = 768;
    private static final String EMBEDDING_MODEL = "gemini-embedding-001";


    public static EmbeddingStore<TextSegment> createQdrantStore() {
        String host = requiredEnv("QDRANT_HOST");
        int port = Integer.parseInt(envOrDefault("QDRANT_PORT", "6334"));
        boolean useTls = Boolean.parseBoolean(envOrDefault("QDRANT_TLS", "true"));
        String apiKey = requiredEnv("QDRANT_API_KEY");

        // Original langchain4j store: still used for all writes (ingestion).
        EmbeddingStore<TextSegment> langchainStore = QdrantEmbeddingStore.builder()
                .host(host)
                .port(port)
                .useTls(useTls)
                .apiKey(apiKey)
                .collectionName(COLLECTION_NAME)
                .build();

        // Its search() is broken against current Qdrant servers (stored vector comes back empty ->
        // "Length of vector a (0) must be equal to the length of vector b (768)"),
        // so search goes through our own gRPC call. See SafeQdrantEmbeddingStore.
        QdrantClient searchClient = new QdrantClient(
                QdrantGrpcClient.newBuilder(host, port, useTls)
                        .withApiKey(apiKey)
                        .build());

        return new SafeQdrantEmbeddingStore(langchainStore, searchClient, COLLECTION_NAME);
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
