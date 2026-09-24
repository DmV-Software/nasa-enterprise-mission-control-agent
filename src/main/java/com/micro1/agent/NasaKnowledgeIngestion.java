package com.micro1.agent;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiTokenizer;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

public class NasaKnowledgeIngestion {

    private static final String COLLECTION_NAME =
            "nasa_knowledge";

    /*
     * LangChain4j 0.35.0 / Google AI Gemini.
     */
    private static final String EMBEDDING_MODEL =
            "embedding-001";

    /*
     * Keep this value identical between ingestion and retrieval.
     *
     * 768 gives a good balance between vector size and retrieval quality.
     */
    private static final int EMBEDDING_DIMENSION =
            768;

    /*
     * Token-aware chunking.
     *
     * The tokenizer itself is provided by Gemini and therefore
     * uses the same tokenization family as the embedding service.
     */
    private static final int MAX_SEGMENT_SIZE =
            800;

    private static final int MAX_OVERLAP_SIZE =
            120;

    /*
     * A deliberately curated initial NASA documentation set.
     *
     * These are knowledge/documentation sources, not live-data APIs.
     */
    private static final List<String> NASA_SOURCES = List.of(

            "https://www.nasa.gov/mission/dsn/",

            "https://science.nasa.gov/mission/mars-exploration-rovers/",

            "https://science.nasa.gov/mission/mars-science-laboratory/",

            "https://science.nasa.gov/mission/james-webb-space-telescope/",

            "https://science.nasa.gov/universe/exoplanets/",

            "https://science.nasa.gov/solar-system/comets/",

            "https://science.nasa.gov/solar-system/asteroids/",

            "https://science.nasa.gov/solar-system/earth/",

            "https://www.nasa.gov/solar-system/"
    );

    private final EmbeddingModel embeddingModel;

    private final EmbeddingStore<TextSegment>
            embeddingStore;

    private final GoogleAiGeminiTokenizer tokenizer;

    public NasaKnowledgeIngestion() {

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
         * LangChain4j 0.35.0 Google AI embedding model.
         */
        this.embeddingModel =
                GoogleAiEmbeddingModel.builder()
                        .apiKey(geminiApiKey)
                        .modelName(EMBEDDING_MODEL)
                        .taskType(
                                GoogleAiEmbeddingModel.TaskType
                                        .RETRIEVAL_DOCUMENT
                        )
                        .outputDimensionality(
                                EMBEDDING_DIMENSION
                        )
                        .maxRetries(3)
                        .build();

        /*
         * Gemini tokenizer is useful for token-aware chunking.
         */
        this.tokenizer =
                GoogleAiGeminiTokenizer.builder()
                        .apiKey(geminiApiKey)
                        .modelName(
                                "gemini-1.5-flash"
                        )
                        .build();

        /*
         * Qdrant Cloud.
         *
         * Cloud uses TLS and API-key authentication.
         */
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

    public static void main(String[] args) {

        System.out.println();
        System.out.println(
                "================================================"
        );
        System.out.println(
                "           NASA KNOWLEDGE INGESTION"
        );
        System.out.println(
                "================================================"
        );

        try {

            NasaKnowledgeIngestion ingestion =
                    new NasaKnowledgeIngestion();

            ingestion.ingestAll();

            System.out.println();
            System.out.println(
                    "[DONE] NASA knowledge ingestion completed."
            );

        } catch (Exception e) {

            System.err.println();
            System.err.println(
                    "[FATAL] NASA knowledge ingestion failed."
            );

            e.printStackTrace();

            System.exit(1);
        }
    }

    public void ingestAll() {

        System.out.println(
                "[INFO] Sources to process: "
                        + NASA_SOURCES.size()
        );

        for (String url : NASA_SOURCES) {

            try {

                ingestSource(url);

            } catch (Exception e) {

                /*
                 * One broken NASA page must not prevent
                 * the remaining knowledge base from being updated.
                 */
                System.err.println(
                        "[ERROR] Failed to ingest: "
                                + url
                );

                System.err.println(
                        "[ERROR] "
                                + e.getMessage()
                );
            }
        }
    }

    private void ingestSource(String url)
            throws Exception {

        System.out.println();
        System.out.println(
                "[LOAD] " + url
        );

        /*
         * Load and parse HTML.
         */
        org.jsoup.nodes.Document html =
                Jsoup.connect(url)
                        .userAgent(
                                "NASA-Enterprise-Mission-Control-Agent/1.0"
                        )
                        .timeout(30_000)
                        .followRedirects(true)
                        .get();

        /*
         * Extract a meaningful title.
         */
        String title =
                extractTitle(html);

        /*
         * Prefer <main> content when available.
         * This avoids embedding navigation/footer noise.
         */
        Element main =
                html.selectFirst("main");

        String text;

        if (main != null) {
            text = main.text();
        } else {
            text = html.body() != null
                    ? html.body().text()
                    : "";
        }

        text = normalizeText(text);

        if (text.isBlank()) {

            throw new IllegalStateException(
                    "No usable text extracted."
            );
        }

        String documentId =
                sha256(url);

        String contentHash =
                sha256(text);

        System.out.println(
                "[TITLE] " + title
        );

        System.out.println(
                "[DOCUMENT] " + documentId
        );

        System.out.println(
                "[HASH] " + contentHash
        );

        /*
         * Remove the previous version of this source.
         *
         * This makes re-ingestion safe:
         *
         * old chunks -> removed
         * new chunks -> inserted
         *
         * No duplicate accumulation.
         */
        removeExistingDocument(url);

        /*
         * Build a LangChain4j Document with metadata.
         */
        Metadata metadata =
                Metadata.from(
                        "source_url",
                        url
                )
                        .put(
                                "title",
                                title
                        )
                        .put(
                                "document_id",
                                documentId
                        )
                        .put(
                                "content_hash",
                                contentHash
                        )
                        .put(
                                "source_type",
                                "nasa_documentation"
                        );

        Document document =
                Document.from(
                        text,
                        metadata
                );

        /*
         * Token-aware recursive chunking.
         *
         * 800 tokens with 120-token overlap.
         */
        var splitter =
                DocumentSplitters.recursive(
                        MAX_SEGMENT_SIZE,
                        MAX_OVERLAP_SIZE,
                        tokenizer
                );

        List<TextSegment> segments =
                splitter.split(document);

        if (segments.isEmpty()) {

            throw new IllegalStateException(
                    "Document produced zero chunks."
            );
        }

        /*
         * Add chunk-level metadata.
         */
        List<TextSegment> enrichedSegments =
                new ArrayList<>(
                        segments.size()
                );

        for (int i = 0;
             i < segments.size();
             i++) {

            TextSegment original =
                    segments.get(i);

            Metadata chunkMetadata =
                    Metadata.from(
                            "source_url",
                            url
                    )
                            .put(
                                    "title",
                                    title
                            )
                            .put(
                                    "document_id",
                                    documentId
                            )
                            .put(
                                    "content_hash",
                                    contentHash
                            )
                            .put(
                                    "source_type",
                                    "nasa_documentation"
                            )
                            .put(
                                    "chunk_index",
                                    i
                            )
                            .put(
                                    "chunk_count",
                                    segments.size()
                            );

            enrichedSegments.add(
                    TextSegment.from(
                            original.text(),
                            chunkMetadata
                    )
            );
        }

        System.out.println(
                "[CHUNKS] "
                        + enrichedSegments.size()
        );

        /*
         * Batch embedding.
         */
        List<Embedding> embeddings =
                embeddingModel
                        .embedAll(
                                enrichedSegments
                        )
                        .content();

        /*
         * Deterministic UUID per source + chunk index.
         *
         * Qdrant/LangChain4j can therefore use stable IDs
         * instead of generating a different ID on every run.
         */
        List<String> ids =
                new ArrayList<>(
                        enrichedSegments.size()
                );

        for (int i = 0;
             i < enrichedSegments.size();
             i++) {

            String stableKey =
                    documentId
                            + ":"
                            + i;

            ids.add(
                    UUID.nameUUIDFromBytes(
                            stableKey.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    ).toString()
            );
        }

        /*
         * One batch write.
         */
        embeddingStore.addAll(
                ids,
                embeddings,
                enrichedSegments
        );

        System.out.println(
                "[INDEXED] "
                        + enrichedSegments.size()
                        + " chunks"
        );
    }

    private void removeExistingDocument(
            String sourceUrl) {

        try {

            embeddingStore.removeAll(
                    metadataKey("source_url")
                            .isEqualTo(sourceUrl)
            );

            System.out.println(
                    "[QDRANT] Previous chunks removed."
            );

        } catch (Exception e) {

            /*
             * Do not silently continue here.
             *
             * If old chunks cannot be removed,
             * inserting new chunks could create duplicates.
             */
            throw new IllegalStateException(
                    "Cannot remove previous document version "
                            + "from Qdrant.",
                    e
            );
        }
    }

    private static String extractTitle(
            org.jsoup.nodes.Document html) {

        String title =
                html.title();

        if (title != null
                && !title.isBlank()) {

            return normalizeText(title);
        }

        Element heading =
                html.selectFirst("h1");

        if (heading != null
                && !heading.text().isBlank()) {

            return normalizeText(
                    heading.text()
            );
        }

        return "NASA Documentation";
    }

    private static String normalizeText(
            String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace('\u00A0', ' ')
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private static String sha256(
            String value)
            throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );

        byte[] hash =
                digest.digest(
                        value.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        StringBuilder result =
                new StringBuilder(
                        hash.length * 2
                );

        for (byte b : hash) {

            result.append(
                    String.format(
                            "%02x",
                            b
                    )
            );
        }

        return result.toString();
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