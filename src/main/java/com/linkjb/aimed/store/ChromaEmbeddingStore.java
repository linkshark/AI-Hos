package com.linkjb.aimed.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ChromaEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(ChromaEmbeddingStore.class);
    private static final int BATCH_SIZE = 200;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String tenant;
    private final String database;
    private final String collectionName;
    private final Duration timeout;
    private final Object collectionMonitor = new Object();
    private volatile String collectionId;

    public ChromaEmbeddingStore(String baseUrl,
                                String tenant,
                                String database,
                                String collectionName,
                                Duration timeout,
                                ObjectMapper objectMapper) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.tenant = tenant;
        this.database = database;
        this.collectionName = collectionName;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    @Override
    public String add(Embedding embedding) {
        String id = generateIds(1).get(0);
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addAll(List.of(id), List.of(embedding), List.of(TextSegment.from("")));
    }

    @Override
    public String add(Embedding embedding, TextSegment embedded) {
        String id = generateIds(1).get(0);
        addAll(List.of(id), List.of(embedding), List.of(embedded));
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = generateIds(embeddings.size());
        addAll(ids, embeddings, List.of());
        return ids;
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (ids == null || embeddings == null || ids.isEmpty() || embeddings.isEmpty()) {
            return;
        }
        if (ids.size() != embeddings.size()) {
            throw new IllegalArgumentException("Chroma upsert ids 和 embeddings 数量不一致");
        }
        if (!embedded.isEmpty() && embedded.size() != embeddings.size()) {
            throw new IllegalArgumentException("Chroma upsert embedded 和 embeddings 数量不一致");
        }

        String resolvedCollectionId = resolveCollectionId(true);
        try {
            resolvedCollectionId = ensureCompatibleCollection(resolvedCollectionId, embeddings.get(0).dimension());
            doUpsertBatches(resolvedCollectionId, ids, embeddings, embedded);
        } catch (IllegalStateException e) {
            if (!isDimensionMismatch(e)) {
                throw e;
            }
            log.warn("Chroma collection 维度不兼容，自动重建后重试 collection={}", collectionName);
            resetCollection(resolvedCollectionId);
            doUpsertBatches(resolveCollectionId(true), ids, embeddings, embedded);
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String resolvedCollectionId = resolveCollectionId(false);
        if (!StringUtils.hasText(resolvedCollectionId)) {
            return;
        }
        List<String> allIds = ids.stream().filter(StringUtils::hasText).toList();
        for (int start = 0; start < allIds.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, allIds.size());
            Map<String, Object> payload = Map.of("ids", allIds.subList(start, end));
            sendJson("POST", collectionPath(resolvedCollectionId) + "/delete", payload, Set.of(200));
        }
    }

    @Override
    public void removeAll() {
        String resolvedCollectionId = resolveCollectionId(false);
        if (!StringUtils.hasText(resolvedCollectionId)) {
            return;
        }
        sendJson("DELETE", collectionNamePath(), null, Set.of(200, 404));
        collectionId = null;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        Objects.requireNonNull(request, "request");
        String resolvedCollectionId = resolveCollectionId(false);
        if (!StringUtils.hasText(resolvedCollectionId)) {
            return new EmbeddingSearchResult<>(List.of());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query_embeddings", List.of(request.queryEmbedding().vectorAsList()));
        payload.put("n_results", request.maxResults());
        payload.put("include", List.of("documents", "metadatas", "distances"));

        JsonNode root = sendJson("POST", collectionPath(resolvedCollectionId) + "/query", payload, Set.of(200));
        JsonNode idsNode = root.path("ids");
        JsonNode documentsNode = root.path("documents");
        JsonNode metadatasNode = root.path("metadatas");
        JsonNode distancesNode = root.path("distances");

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        JsonNode firstIds = idsNode.isArray() && idsNode.size() > 0 ? idsNode.get(0) : null;
        JsonNode firstDocs = documentsNode.isArray() && documentsNode.size() > 0 ? documentsNode.get(0) : null;
        JsonNode firstMetadatas = metadatasNode.isArray() && metadatasNode.size() > 0 ? metadatasNode.get(0) : null;
        JsonNode firstDistances = distancesNode.isArray() && distancesNode.size() > 0 ? distancesNode.get(0) : null;
        if (firstIds == null || !firstIds.isArray()) {
            return new EmbeddingSearchResult<>(List.of());
        }

        for (int i = 0; i < firstIds.size(); i++) {
            String id = firstIds.get(i).asText();
            String document = firstDocs != null && firstDocs.isArray() && i < firstDocs.size() && !firstDocs.get(i).isNull()
                    ? firstDocs.get(i).asText("")
                    : "";
            Metadata metadata = firstMetadatas != null && firstMetadatas.isArray() && i < firstMetadatas.size() && !firstMetadatas.get(i).isNull()
                    ? Metadata.from(objectMapper.convertValue(firstMetadatas.get(i), Map.class))
                    : new Metadata();
            Double distance = firstDistances != null && firstDistances.isArray() && i < firstDistances.size() && !firstDistances.get(i).isNull()
                    ? firstDistances.get(i).asDouble()
                    : null;
            double score = distanceToScore(distance);
            if (score < request.minScore()) {
                continue;
            }
            matches.add(new EmbeddingMatch<>(score, id, null, TextSegment.from(document, metadata)));
        }
        return new EmbeddingSearchResult<>(matches);
    }

    public Set<String> existingIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        String resolvedCollectionId = resolveCollectionId(false);
        if (!StringUtils.hasText(resolvedCollectionId)) {
            return Set.of();
        }
        Set<String> existing = new LinkedHashSet<>();
        List<String> allIds = ids.stream().filter(StringUtils::hasText).toList();
        for (int start = 0; start < allIds.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, allIds.size());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ids", allIds.subList(start, end));
            JsonNode root = sendJson("POST", collectionPath(resolvedCollectionId) + "/get", payload, Set.of(200));
            JsonNode idsNode = root.path("ids");
            if (!idsNode.isArray()) {
                continue;
            }
            for (JsonNode idNode : idsNode) {
                if (idNode.isTextual()) {
                    existing.add(idNode.asText());
                }
            }
        }
        return existing;
    }

    private String resolveCollectionId(boolean createIfMissing) {
        String cached = this.collectionId;
        if (StringUtils.hasText(cached)) {
            return cached;
        }
        synchronized (collectionMonitor) {
            if (StringUtils.hasText(this.collectionId)) {
                return this.collectionId;
            }
            String resolved = fetchCollectionId();
            if (!StringUtils.hasText(resolved) && createIfMissing) {
                resolved = createCollection();
            }
            this.collectionId = resolved;
            return resolved;
        }
    }

    private String fetchCollectionId() {
        JsonNode root = sendJson("GET",
                tenantDatabasePath() + "/collections?limit=1000&offset=0",
                null,
                Set.of(200, 404));
        if (root == null || !root.isArray()) {
            return null;
        }
        for (JsonNode node : root) {
            if (collectionName.equals(node.path("name").asText())) {
                return node.path("id").asText(null);
            }
        }
        return null;
    }

    private String createCollection() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", collectionName);
        payload.put("get_or_create", true);
        payload.put("metadata", Map.of("managed_by", "aimed"));
        JsonNode root = sendJson("POST", tenantDatabasePath() + "/collections", payload, Set.of(200));
        String createdCollectionId = root.path("id").asText(null);
        if (!StringUtils.hasText(createdCollectionId)) {
            throw new IllegalStateException("创建 Chroma collection 失败: 未返回 collection 名称");
        }
        return createdCollectionId;
    }

    private void doUpsertBatches(String resolvedCollectionId,
                                 List<String> ids,
                                 List<Embedding> embeddings,
                                 List<TextSegment> embedded) {
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, ids.size());
            List<String> idBatch = ids.subList(start, end);
            List<Embedding> embeddingBatch = embeddings.subList(start, end);
            List<TextSegment> segmentBatch = embedded.isEmpty() ? List.of() : embedded.subList(start, end);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ids", idBatch);
            payload.put("embeddings", embeddingBatch.stream().map(Embedding::vectorAsList).toList());
            if (!segmentBatch.isEmpty()) {
                payload.put("documents", segmentBatch.stream().map(TextSegment::text).toList());
                payload.put("metadatas", segmentBatch.stream().map(this::toMetadataMap).toList());
            }
            sendJson("POST", collectionPath(resolvedCollectionId) + "/upsert", payload, Set.of(200));
        }
    }

    private String ensureCompatibleCollection(String currentCollectionId, int expectedDimension) {
        Integer dimension = readCollectionDimension(currentCollectionId);
        if (dimension == null || dimension == expectedDimension) {
            return currentCollectionId;
        }
        log.warn("Chroma collection 维度不匹配，准备重建 collection={} currentDimension={} expectedDimension={}",
                collectionName, dimension, expectedDimension);
        resetCollection(currentCollectionId);
        return resolveCollectionId(true);
    }

    private Integer readCollectionDimension(String currentCollectionId) {
        JsonNode root = sendJson("GET", collectionNamePath(), null, Set.of(200, 404));
        if (root == null || root.isMissingNode()) {
            return null;
        }
        JsonNode dimensionNode = root.path("dimension");
        return dimensionNode.isInt() ? dimensionNode.asInt() : null;
    }

    private void resetCollection(String currentCollectionId) {
        sendJson("DELETE", collectionNamePath(), null, Set.of(200, 404));
        collectionId = null;
    }

    private String tenantDatabasePath() {
        return "/api/v2/tenants/" + encode(tenant) + "/databases/" + encode(database);
    }

    private String collectionPath(String collectionId) {
        return tenantDatabasePath() + "/collections/" + encode(collectionId);
    }

    private String collectionNamePath() {
        return tenantDatabasePath() + "/collections/" + encode(collectionName);
    }

    private Map<String, Object> toMetadataMap(TextSegment segment) {
        if (segment == null || segment.metadata() == null) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : segment.metadata().toMap().entrySet()) {
            Object value = entry.getValue();
            if (value == null
                    || value instanceof String
                    || value instanceof Number
                    || value instanceof Boolean) {
                normalized.put(entry.getKey(), value);
            } else {
                normalized.put(entry.getKey(), value.toString());
            }
        }
        return normalized;
    }

    private double distanceToScore(Double distance) {
        if (distance == null) {
            return 0.0;
        }
        return 1.0 / (1.0 + Math.max(distance, 0.0));
    }

    private boolean isDimensionMismatch(IllegalStateException exception) {
        return exception.getMessage() != null && exception.getMessage().contains("expecting embedding with dimension");
    }

    private JsonNode sendJson(String method, String path, Object body, Set<Integer> acceptableStatuses) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("Accept", "application/json");
            if (body != null) {
                requestBuilder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
            } else {
                requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (!acceptableStatuses.contains(response.statusCode())) {
                throw new IllegalStateException("Chroma 请求失败: method=" + method + ", path=" + path + ", status=" + response.statusCode() + ", body=" + response.body());
            }
            if (!StringUtils.hasText(response.body())) {
                return null;
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("调用 Chroma 失败: " + method + " " + path, e);
        } catch (IOException e) {
            throw new IllegalStateException("调用 Chroma 失败: " + method + " " + path, e);
        }
    }

    private static String trimTrailingSlash(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
