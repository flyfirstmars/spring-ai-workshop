package ge.jar.springaiworkshop.voyagermate.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

/**
 * Service for loading and processing travel documents using Spring AI's ETL pipeline.
 * Demonstrates DocumentReader and Transformer patterns without vector storage.
 * Documents processed here will be ready for RAG integration in Session 4.
 */
@Service
public class TravelDocumentService {

    private final TokenTextSplitter textSplitter;
    private final ObjectMapper objectMapper;
    private final PathMatchingResourcePatternResolver resourceResolver;

    private final List<Document> loadedDocuments = new CopyOnWriteArrayList<>();

    public TravelDocumentService(TokenTextSplitter textSplitter, ObjectMapper objectMapper) {
        this.textSplitter = textSplitter;
        this.objectMapper = objectMapper;
        this.resourceResolver = new PathMatchingResourcePatternResolver();
    }

    /**
     * Loads and processes all travel guide markdown files.
     * Uses TextReader for markdown files and applies chunking.
     *
     * @return number of document chunks created
     */
    public int loadTravelGuides() {
        List<Document> guides = new ArrayList<>();

        try {
            Resource[] resources = resourceResolver.getResources("classpath:data/travel-guides/*.md");

            for (Resource resource : resources) {
                List<Document> docs = loadMarkdownResource(resource);
                guides.addAll(docs);
            }

            // Apply chunking to large documents
            List<Document> chunkedDocs = textSplitter.apply(guides);
            loadedDocuments.addAll(chunkedDocs);

            return chunkedDocs.size();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load travel guides", e);
        }
    }

    /**
     * Loads and processes the destinations JSON file.
     * Converts each destination into a Document with metadata.
     *
     * @return number of destination documents created
     */
    public int loadDestinationData() {
        try {
            Resource resource = resourceResolver.getResource("classpath:data/destinations.json");
            String json = resource.getContentAsString(StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(json);

            List<Document> docs = new ArrayList<>();

            // Process destinations array
            JsonNode destinations = root.get("destinations");
            if (destinations != null && destinations.isArray()) {
                for (JsonNode dest : destinations) {
                    Document doc = createDestinationDocument(dest);
                    docs.add(doc);
                }
            }

            // Process travel tips
            JsonNode travelTips = root.get("travelTips");
            if (travelTips != null && travelTips.isArray()) {
                for (JsonNode tipCategory : travelTips) {
                    Document doc = createTravelTipDocument(tipCategory);
                    docs.add(doc);
                }
            }

            loadedDocuments.addAll(docs);
            return docs.size();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load destination data", e);
        }
    }

    /**
     * Returns all currently loaded documents.
     */
    public List<Document> getLoadedDocuments() {
        return List.copyOf(loadedDocuments);
    }

    /**
     * Clears all loaded documents from memory.
     */
    public void clearDocuments() {
        loadedDocuments.clear();
    }

    /**
     * Returns the count of loaded documents.
     */
    public int getDocumentCount() {
        return loadedDocuments.size();
    }

    /**
     * Simple keyword search across loaded documents.
     * This is a basic text search, not semantic search (that comes in Session 4).
     *
     * @param query search query
     * @return matching documents
     */
    public List<Document> searchDocuments(String query) {
        String lowerQuery = query.toLowerCase();
        return loadedDocuments.stream()
                .filter(doc -> {
                    assert doc.getText() != null;
                    return doc.getText().toLowerCase().contains(lowerQuery);
                })
                .toList();
    }

    /**
     * Gets documents by source type.
     *
     * @param sourceType "markdown" or "json"
     * @return filtered documents
     */
    public List<Document> getDocumentsByType(String sourceType) {
        return loadedDocuments.stream()
                .filter(doc -> sourceType.equals(doc.getMetadata().get("source_type")))
                .toList();
    }

    private List<Document> loadMarkdownResource(Resource resource) throws IOException {
        TextReader reader = new TextReader(resource);
        reader.getCustomMetadata().put("source_type", "markdown");
        reader.getCustomMetadata().put("file_name", resource.getFilename());
        return reader.get();
    }

    private Document createDestinationDocument(JsonNode dest) {
        String name = dest.path("name").asText();
        String country = dest.path("country").asText();
        String description = dest.path("description").asText();

        StringBuilder content = new StringBuilder();
        content.append("Destination: ").append(name).append(", ").append(country).append("\n");
        content.append("Description: ").append(description).append("\n");

        JsonNode highlights = dest.path("highlights");
        if (highlights.isArray()) {
            content.append("Highlights: ");
            List<String> highlightList = new ArrayList<>();
            highlights.forEach(h -> highlightList.add(h.asText()));
            content.append(String.join(", ", highlightList)).append("\n");
        }

        JsonNode bestMonths = dest.path("bestMonths");
        if (bestMonths.isArray()) {
            content.append("Best months to visit: ");
            List<String> months = new ArrayList<>();
            bestMonths.forEach(m -> months.add(m.asText()));
            content.append(String.join(", ", months)).append("\n");
        }

        JsonNode budget = dest.path("avgDailyBudget");
        if (!budget.isMissingNode()) {
            content.append("Daily budget: $")
                    .append(budget.path("budget").asInt())
                    .append(" (budget) / $")
                    .append(budget.path("midRange").asInt())
                    .append(" (mid-range) / $")
                    .append(budget.path("luxury").asInt())
                    .append(" (luxury)\n");
        }

        JsonNode tags = dest.path("tags");
        if (tags.isArray()) {
            content.append("Tags: ");
            List<String> tagList = new ArrayList<>();
            tags.forEach(t -> tagList.add(t.asText()));
            content.append(String.join(", ", tagList));
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source_type", "json");
        metadata.put("document_type", "destination");
        metadata.put("destination_id", dest.path("id").asText());
        metadata.put("destination_name", name);
        metadata.put("country", country);
        metadata.put("continent", dest.path("continent").asText());

        return new Document(content.toString(), metadata);
    }

    private Document createTravelTipDocument(JsonNode tipCategory) {
        String category = tipCategory.path("category").asText();

        StringBuilder content = new StringBuilder();
        content.append("Travel Tips - ").append(category.toUpperCase()).append("\n\n");

        JsonNode tips = tipCategory.path("tips");
        if (tips.isArray()) {
            tips.forEach(tip -> content.append("- ").append(tip.asText()).append("\n"));
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source_type", "json");
        metadata.put("document_type", "travel_tips");
        metadata.put("category", category);

        return new Document(content.toString(), metadata);
    }
}








