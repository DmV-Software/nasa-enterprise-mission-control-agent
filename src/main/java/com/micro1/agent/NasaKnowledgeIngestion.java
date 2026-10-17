package com.nasa.agent;


import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiTokenizer;
import dev.langchain4j.store.embedding.EmbeddingStore;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;


import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;


public class NasaKnowledgeIngestion {


    private static final int MAX_SEGMENT_SIZE = 800;
    private static final int MAX_OVERLAP_SIZE = 120;


    private static final List<String> NASA_SOURCES = List.of(
            "https://science.nasa.gov/solar-system/",
            "https://science.nasa.gov/solar-system/planets/",
            "https://science.nasa.gov/exoplanets/",
            "https://science.nasa.gov/mission/webb/",
            "https://science.nasa.gov/solar-system/comets/",
            "https://science.nasa.gov/solar-system/asteroids/",
            "https://www.nasa.gov/directorates/stmd/tech-demo-missions-program/nuclear-thermal-propulsion-game-changing-technology-for-deep-space-exploration/",
            "https://techport.nasa.gov/projects/94105",
            "https://www.nasa.gov/humans-in-space/"
    );


    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final GoogleAiGeminiTokenizer tokenizer;


    // Injected dependencies
    public NasaKnowledgeIngestion(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.tokenizer = GoogleAiGeminiTokenizer.builder()
                .apiKey(VectorDbConfig.resolveGeminiApiKey())
                .modelName("gemini-1.5-flash")
                .logRequestsAndResponses(false)
                .build();
    }


    public static void main(String[] args) {
        System.out.println("\n================================================");
        System.out.println("           NASA KNOWLEDGE INGESTION");
        System.out.println("================================================\n");


        try {
            System.out.println("[STATUS] Initializing Infrastructure...");
            EmbeddingStore<TextSegment> store = VectorDbConfig.createQdrantStore();
            EmbeddingModel model = VectorDbConfig.createIngestionModel();


            NasaKnowledgeIngestion ingestion = new NasaKnowledgeIngestion(model, store);
            ingestion.ingestAll();


            System.out.println("\n[DONE] NASA knowledge ingestion completed.");
            System.exit(0);


        } catch (Exception e) {
            System.err.println("\n[FATAL] NASA knowledge ingestion failed.");
            e.printStackTrace();
            System.exit(1);
        }
    }


    public void ingestAll() {
        System.out.println("[INFO] Sources to process: " + NASA_SOURCES.size());
        for (String url : NASA_SOURCES) {
            try {
                ingestSource(url);
            } catch (Exception e) {
                System.err.println("[ERROR] Failed to ingest: " + url + " - " + e.getMessage());
            }
        }
    }


    private void ingestSource(String url) throws Exception {
        System.out.println("\n[LOAD] " + url);


        org.jsoup.nodes.Document html = Jsoup.connect(url)
                .userAgent("NASA-Enterprise-Mission-Control-Agent/1.0")
                .timeout(30_000).followRedirects(true).get();


        String title = extractTitle(html);
        Element main = html.selectFirst("main");
        String text = main != null ? main.text() : (html.body() != null ? html.body().text() : "");
        text = normalizeText(text);


        if (text.isBlank()) throw new IllegalStateException("No usable text extracted.");


        String documentId = sha256(url);
        String contentHash = sha256(text);


        System.out.println("[TITLE] " + title);
        System.out.println("[DOCUMENT] " + documentId);


        removeExistingDocument(url);


        Metadata metadata = Metadata.from("source_url", url)
                .put("title", title).put("document_id", documentId)
                .put("content_hash", contentHash).put("source_type", "nasa_documentation");


        Document document = Document.from(text, metadata);
        var splitter = DocumentSplitters.recursive(MAX_SEGMENT_SIZE, MAX_OVERLAP_SIZE);
        List<TextSegment> segments = splitter.split(document);


        if (segments.isEmpty()) throw new IllegalStateException("Document produced zero chunks.");


        List<TextSegment> enrichedSegments = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            String chunkText = segments.get(i).text().trim();
            if (!chunkText.isEmpty()) {
                Metadata chunkMetadata = metadata.copy()
                        .put("chunk_index", i).put("chunk_count", segments.size());
                enrichedSegments.add(TextSegment.from(chunkText, chunkMetadata));
            }
        }

        System.out.println("[CHUNKS] " + enrichedSegments.size());


        List<Embedding> embeddings = embeddingModel.embedAll(enrichedSegments).content();
        embeddingStore.addAll(embeddings, enrichedSegments);


        System.out.println("[INDEXED] " + enrichedSegments.size() + " chunks");
    }


    private void removeExistingDocument(String sourceUrl) {
        try {
            embeddingStore.removeAll(metadataKey("source_url").isEqualTo(sourceUrl));
        } catch (Exception e) {
            //throw new IllegalStateException("Cannot remove previous document version from Qdrant.", e);
        }
    }


    private static String extractTitle(org.jsoup.nodes.Document html) {
        String title = html.title();
        if (title != null && !title.isBlank()) return normalizeText(title);
        Element heading = html.selectFirst("h1");
        if (heading != null && !heading.text().isBlank()) return normalizeText(heading.text());
        return "NASA Documentation";
    }


    private static String normalizeText(String text) {
        return text == null ? "" : text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }


    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte b : hash) result.append(String.format("%02x", b));
        return result.toString();
    }
}
