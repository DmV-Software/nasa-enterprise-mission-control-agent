package com.micro1.agent;

import dev.langchain4j.data.document.Metadata;
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

    /*
     * Must match the dimension used during ingestion.
     */
    private static final int EMBEDDING_DIMENSION =
            768;

    /*
     * LangChain4j 0.35.0 / Google AI Gemini.
     */
    private static final String EMBEDDING_MODEL =
            "embedding-001";

    /*
     * Number of candidates returned to the agent.
     */
    private static final int MAX_RESULTS =
            5;

    /*
     * Minimum semantic similarity.
     *
     * This is deliberately not extremely high:
     * retrieval should return useful candidates,
     * while the agent decides how they should be used.
     */
    private static final double MIN_SCORE =
            0.70;

    private final EmbeddingModel embeddingModel;

    private final EmbeddingStore<TextSegment>
            embeddingStore;

    public NasaKnowledgeRetrieval() {

        String geminiApiKey =
                resolveGeminiApiKey();

        String qdrantHost =
                requiredEnv("QDRANT_HOST");

        String qdrantApiKey =
                requiredEnv("QDRANT_API_KEY");

        int qdrantPort =
                Integer.parseInt(
                        envOrDefault(
                                "QDRANT_PORT",
                                "6334"
                        )
                );

        boolean qdrantTls =
                Boolean.parseBoolean(
                        envOrDefault(
                                "QDRANT_TLS",
                                "true"
                        )
                );

        /*
         * IMPORTANT:
         *
         * Retrieval uses RETRIEVAL_QUERY,
         * while ingestion uses RETRIEVAL_DOCUMENT.
         */
        this.embeddingModel =
                GoogleAiEmbeddingModel.builder()
                        .apiKey(geminiApiKey)
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
                        .useTls(qdrantTls)
                        .apiKey(qdrantApiKey)
                        .collectionName(
                                COLLECTION_NAME
                        )
                        .build();
    }

    public String search(
            String query) {

        if (query == null
                || query.isBlank()) {

            return "NASA knowledge query is empty.";
        }

        String normalizedQuery =
                query.trim();

        try {

            Embedding queryEmbedding =
                    embeddingModel
                            .embed(
                                    normalizedQuery
                            )
                            .content();

            EmbeddingSearchRequest request =
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(
                                    queryEmbedding
                            )
                            .maxResults(
                                    MAX_RESULTS
                            )
                            .minScore(
                                    MIN_SCORE
                            )
                            .build();

            List<EmbeddingMatch<TextSegment>>
                    matches =
                    embeddingStore
                            .search(request)
                            .matches();

            if (matches.isEmpty()) {

                return
                        "No sufficiently relevant NASA "
                                + "documentation was found "
                                + "for the query.";
            }

            return formatResults(
                    normalizedQuery,
                    matches
            );

        } catch (Exception e) {

            /*
             * Retrieval failure is explicit.
             *
             * We do not convert infrastructure errors
             * into an apparently valid empty result.
             */
            return
                    "NASA_KNOWLEDGE_RETRIEVAL_ERROR: "
                            + e.getMessage();
        }
    }

    private String formatResults(
            String query,
            List<EmbeddingMatch<TextSegment>>
                    matches) {

        StringBuilder result =
                new StringBuilder();

        result.append(
                "NASA knowledge retrieval results "
                        + "for query: \""
                        + query
                        + "\"\n\n"
        );

        int rank = 1;

        for (EmbeddingMatch<TextSegment> match
                : matches) {

            TextSegment segment =
                    match.embedded();

            Metadata metadata =
                    segment.metadata();

            String title =
                    metadata.getString(
                            "title"
                    );

            String sourceUrl =
                    metadata.getString(
                            "source_url"
                    );

            String documentId =
                    metadata.getString(
                            "document_id"
                    );

            String contentHash =
                    metadata.getString(
                            "content_hash"
                    );

            String chunkIndex =
                    metadata.getString(
                            "chunk_index"
                    );

            result.append(
                    "SOURCE "
                            + rank
                            + "\n"
            );

            result.append(
                    "Title: "
                            + safe(title)
                            + "\n"
            );

            result.append(
                    "URL: "
                            + safe(sourceUrl)
                            + "\n"
            );

            result.append(
                    "Document ID: "
                            + safe(documentId)
                            + "\n"
            );

            result.append(
                    "Content hash: "
                            + safe(contentHash)
                            + "\n"
            );

            result.append(
                    "Chunk: "
                            + safe(chunkIndex)
                            + "\n"
            );

            result.append(
                    String.format(
                            "Relevance score: %.4f%n",
                            match.score()
                    )
            );

            result.append(
                    "Content:\n"
            );

            result.append(
                    segment.text()
            );

            result.append(
                    "\n\n"
            );

            rank++;
        }

        return result
                .toString()
                .trim();
    }

    private static String safe(
            String value) {

        return value == null
                ? ""
                : value;
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
                "Gemini API key not found."
        );
    }

    private static String requiredEnv(
            String name) {

        String value =
                System.getenv(name);

        if (value == null
                || value.isBlank()) {

            throw new IllegalStateException(
                    "Required environment variable "
                            + name
                            + " is not set."
            );
        }

        return value.trim();
    }

    private static String envOrDefault(
            String name,
            String defaultValue) {

        String value =
                System.getenv(name);

        if (value == null
                || value.isBlank()) {

            return defaultValue;
        }

        return value.trim();
    }
}