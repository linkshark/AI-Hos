package com.linkjb.aimed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalMatchedDiseaseEntity;
import com.linkjb.aimed.entity.dto.response.medical.MedicalStandardSummaryResponse;
import com.linkjb.aimed.entity.vo.medical.MedicalSymptomMappingItem;
import com.linkjb.aimed.entity.dto.request.medical.MedicalSymptomMappingRequest;
import com.linkjb.aimed.entity.MedicalConcept;
import com.linkjb.aimed.entity.MedicalConceptAlias;
import com.linkjb.aimed.entity.MedicalSymptomMapping;
import com.linkjb.aimed.mapper.MedicalConceptAliasMapper;
import com.linkjb.aimed.mapper.MedicalConceptMapper;
import com.linkjb.aimed.mapper.MedicalDocMappingMapper;
import com.linkjb.aimed.mapper.MedicalSymptomMappingMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 疾病标准查询与维护服务。
 *
 * 这里把“疾病标准化”限定为本地库查询，不在问答实时链路里依赖外部标准接口，保证演示和生产部署都可控。
 */
@Service
public class MedicalStandardLookupService {

    private final MedicalConceptMapper medicalConceptMapper;
    private final MedicalConceptAliasMapper medicalConceptAliasMapper;
    private final MedicalSymptomMappingMapper medicalSymptomMappingMapper;
    private final MedicalDocMappingMapper medicalDocMappingMapper;
    private static final long LOOKUP_CACHE_TTL_MS = 5 * 60 * 1000L;
    private final ConcurrentMap<String, CacheEntry<List<KnowledgeRetrievalMatchedDiseaseEntity>>> diseaseMatchCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CacheEntry<List<String>>> symptomMatchCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CacheEntry<List<String>>> chiefComplaintMatchCache = new ConcurrentHashMap<>();

    public MedicalStandardLookupService(MedicalConceptMapper medicalConceptMapper,
                                        MedicalConceptAliasMapper medicalConceptAliasMapper,
                                        MedicalSymptomMappingMapper medicalSymptomMappingMapper,
                                        MedicalDocMappingMapper medicalDocMappingMapper) {
        this.medicalConceptMapper = medicalConceptMapper;
        this.medicalConceptAliasMapper = medicalConceptAliasMapper;
        this.medicalSymptomMappingMapper = medicalSymptomMappingMapper;
        this.medicalDocMappingMapper = medicalDocMappingMapper;
    }

    public MedicalStandardSummaryResponse summary() {
        long entityCount = medicalConceptMapper.selectCount(new LambdaQueryWrapper<>());
        long aliasCount = medicalConceptAliasMapper.selectCount(new LambdaQueryWrapper<MedicalConceptAlias>()
                .eq(MedicalConceptAlias::getStatus, "ACTIVE"));
        long mappingCount = medicalSymptomMappingMapper.selectCount(new LambdaQueryWrapper<MedicalSymptomMapping>()
                .eq(MedicalSymptomMapping::getEnabled, true));
        long docMappingCount = medicalDocMappingMapper.selectCount(new LambdaQueryWrapper<>());
        MedicalConcept latest = medicalConceptMapper.selectOne(new LambdaQueryWrapper<MedicalConcept>()
                .isNotNull(MedicalConcept::getVersionTag)
                .ne(MedicalConcept::getVersionTag, "")
                .orderByDesc(MedicalConcept::getUpdatedAt)
                .last("LIMIT 1"));
        return new MedicalStandardSummaryResponse(
                latest == null ? null : latest.getVersionTag(),
                Math.toIntExact(entityCount),
                Math.toIntExact(aliasCount),
                Math.toIntExact(mappingCount),
                Math.toIntExact(docMappingCount)
        );
    }

    public List<KnowledgeRetrievalMatchedDiseaseEntity> matchDiseaseEntities(String query, int limit) {
        String normalized = normalizeKeyword(query);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        String cacheKey = normalized + "#" + Math.max(1, limit);
        CacheEntry<List<KnowledgeRetrievalMatchedDiseaseEntity>> cached = diseaseMatchCache.get(cacheKey);
        if (cached != null && !cached.expired()) {
            return cached.value();
        }
        Set<String> tokens = new LinkedHashSet<>();
        KnowledgeSearchLexicon.detectMedicalAnchors(normalized).forEach(tokens::add);
        KnowledgeSearchLexicon.extractMedicalRewriteHints(normalized).forEach(tokens::add);
        if (tokens.isEmpty()) {
            tokens.add(normalized);
        }
        Map<String, MatchInfo> matches = new LinkedHashMap<>();
        tokens.stream().limit(8).forEach(token -> collectMatchesForToken(token, matches, limit));
        List<KnowledgeRetrievalMatchedDiseaseEntity> result = loadConceptsByPriority(matches.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> (int) Math.round(entry.getValue().score() * 100),
                                Math::max,
                                LinkedHashMap::new
                        )), limit)
                .stream()
                .map(concept -> {
                    MatchInfo info = matches.getOrDefault(concept.getConceptCode(), new MatchInfo("title", 0.75d));
                    return new KnowledgeRetrievalMatchedDiseaseEntity(
                            concept.getConceptCode(),
                            concept.getStandardCode(),
                            concept.getDiseaseName(),
                            concept.getEnglishName(),
                            info.matchType(),
                            info.score()
                    );
                })
                .toList();
        diseaseMatchCache.put(cacheKey, CacheEntry.of(result));
        return result;
    }

    public List<String> findMatchedSymptoms(String query) {
        String normalized = normalizeKeyword(query);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        CacheEntry<List<String>> cached = symptomMatchCache.get(normalized);
        if (cached != null && !cached.expired()) {
            return cached.value();
        }
        List<String> result = findMatchedMappingTerms(normalized, false);
        symptomMatchCache.put(normalized, CacheEntry.of(result));
        return result;
    }

    public List<String> findMatchedChiefComplaints(String query) {
        String normalized = normalizeKeyword(query);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        CacheEntry<List<String>> cached = chiefComplaintMatchCache.get(normalized);
        if (cached != null && !cached.expired()) {
            return cached.value();
        }
        List<String> result = findMatchedMappingTerms(normalized, true);
        chiefComplaintMatchCache.put(normalized, CacheEntry.of(result));
        return result;
    }

    public List<MedicalSymptomMappingItem> listSymptomMappings(String keyword) {
        return listSymptomMappings(keyword, null);
    }

    public List<MedicalSymptomMappingItem> listSymptomMappings(String keyword, String conceptCode) {
        String normalized = normalizeKeyword(keyword);
        String normalizedConceptCode = emptyToNull(conceptCode);
        Set<String> conceptCodes = new LinkedHashSet<>();
        if (StringUtils.hasText(normalized)) {
            findConceptsByKeyword(normalized, 100).forEach(concept -> conceptCodes.add(concept.getConceptCode()));
            medicalConceptAliasMapper.selectList(new LambdaQueryWrapper<MedicalConceptAlias>()
                            .eq(MedicalConceptAlias::getStatus, "ACTIVE")
                            .like(MedicalConceptAlias::getAlias, normalized)
                            .last("LIMIT 100"))
                    .forEach(alias -> conceptCodes.add(alias.getConceptCode()));
        }
        LambdaQueryWrapper<MedicalSymptomMapping> wrapper = new LambdaQueryWrapper<MedicalSymptomMapping>()
                .orderByDesc(MedicalSymptomMapping::getUpdatedAt, MedicalSymptomMapping::getId)
                .last("LIMIT 200");
        if (StringUtils.hasText(normalizedConceptCode)) {
            wrapper.eq(MedicalSymptomMapping::getConceptCode, normalizedConceptCode);
        }
        if (StringUtils.hasText(normalized)) {
            wrapper.and(nested -> {
                nested.like(MedicalSymptomMapping::getSymptomTerm, normalized)
                        .or().like(MedicalSymptomMapping::getChiefComplaintTerm, normalized);
                if (!conceptCodes.isEmpty()) {
                    nested.or().in(MedicalSymptomMapping::getConceptCode, conceptCodes);
                }
            });
        }
        List<MedicalSymptomMapping> mappings = medicalSymptomMappingMapper.selectList(wrapper);
        Map<String, MedicalConcept> conceptMap = conceptsByCode(mappings.stream()
                .map(MedicalSymptomMapping::getConceptCode)
                .filter(StringUtils::hasText)
                .toList());
        return mappings.stream()
                .map(mapping -> toItem(mapping, conceptMap.get(mapping.getConceptCode())))
                .toList();
    }

    public MedicalSymptomMappingItem saveSymptomMapping(MedicalSymptomMappingRequest request) {
        String symptomTerm = emptyToNull(request.symptomTerm());
        String chiefComplaintTerm = emptyToNull(request.chiefComplaintTerm());
        if (!StringUtils.hasText(symptomTerm) && !StringUtils.hasText(chiefComplaintTerm)) {
            throw new IllegalArgumentException("症状词和主诉词至少填写一个");
        }
        MedicalConcept entity = medicalConceptMapper.selectOne(new LambdaQueryWrapper<MedicalConcept>()
                .eq(MedicalConcept::getConceptCode, request.conceptCode())
                .last("LIMIT 1"));
        if (entity == null) {
            throw new IllegalArgumentException("疾病概念不存在");
        }
        MedicalSymptomMapping existing = request.id() == null ? null : medicalSymptomMappingMapper.selectById(request.id());
        if (request.id() != null && existing == null) {
            throw new IllegalArgumentException("症状主诉映射不存在");
        }
        if (existing == null) {
            existing = medicalSymptomMappingMapper.selectOne(uniqueMappingQuery(
                    request.conceptCode(), symptomTerm, chiefComplaintTerm));
        }
        if (existing == null) {
            existing = new MedicalSymptomMapping();
            existing.setConceptCode(request.conceptCode());
            existing.setCreatedAt(LocalDateTime.now());
        }
        existing.setConceptCode(request.conceptCode());
        existing.setSymptomTerm(symptomTerm);
        existing.setChiefComplaintTerm(chiefComplaintTerm);
        existing.setMappingType(request.mappingType().trim().toUpperCase(Locale.ROOT));
        existing.setWeight(request.weight());
        existing.setSource("ADMIN");
        existing.setEnabled(Boolean.TRUE.equals(request.enabled()));
        existing.setUpdatedAt(LocalDateTime.now());
        if (existing.getId() == null) {
            medicalSymptomMappingMapper.insert(existing);
        } else {
            medicalSymptomMappingMapper.updateById(existing);
        }
        clearLookupCaches();
        return toItem(existing, entity);
    }

    public void deleteSymptomMapping(Long id) {
        if (id != null) {
            medicalSymptomMappingMapper.deleteById(id);
            clearLookupCaches();
        }
    }

    private double scoreForMatchType(String matchType) {
        return switch (matchType) {
            case "chief_complaint" -> 1.0d;
            case "symptom" -> 0.9d;
            case "alias" -> 0.85d;
            default -> 0.75d;
        };
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<MedicalConcept> findConceptsByKeyword(String keyword, int limit) {
        return findConceptsByKeyword(keyword, null, limit);
    }

    private List<MedicalConcept> findConceptsByKeyword(String keyword, String categoryCode, int limit) {
        LambdaQueryWrapper<MedicalConcept> wrapper = new LambdaQueryWrapper<MedicalConcept>()
                .eq(MedicalConcept::getStatus, "ACTIVE");
        if (StringUtils.hasText(categoryCode)) {
            wrapper.eq(MedicalConcept::getCategoryCode, categoryCode);
        }
        return medicalConceptMapper.selectList(wrapper
                .and(nested -> nested.like(MedicalConcept::getConceptCode, keyword)
                        .or().like(MedicalConcept::getStandardCode, keyword)
                        .or().like(MedicalConcept::getIcd10Code, keyword)
                        .or().like(MedicalConcept::getNhsaCode, keyword)
                        .or().like(MedicalConcept::getDiseaseName, keyword)
                        .or().like(MedicalConcept::getEnglishName, keyword))
                .orderByAsc(MedicalConcept::getStandardCode)
                .last("LIMIT " + Math.max(1, limit)));
    }

    private List<MedicalConcept> loadConceptsByPriority(Map<String, Integer> priorities, int limit) {
        return loadConceptsByPriority(priorities, null, limit);
    }

    private List<MedicalConcept> loadConceptsByPriority(Map<String, Integer> priorities, String categoryCode, int limit) {
        if (priorities == null || priorities.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<MedicalConcept> wrapper = new LambdaQueryWrapper<MedicalConcept>()
                .in(MedicalConcept::getConceptCode, priorities.keySet())
                .eq(MedicalConcept::getStatus, "ACTIVE");
        if (StringUtils.hasText(categoryCode)) {
            wrapper.eq(MedicalConcept::getCategoryCode, categoryCode);
        }
        List<MedicalConcept> concepts = medicalConceptMapper.selectList(wrapper);
        return concepts.stream()
                .sorted(Comparator
                        .comparing((MedicalConcept concept) -> priorities.getOrDefault(concept.getConceptCode(), 0)).reversed()
                        .thenComparing(MedicalConcept::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, limit))
                .toList();
    }

    private void collectMatchesForToken(String token, Map<String, MatchInfo> matches, int limit) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        findConceptsByKeyword(token, Math.max(limit, 20)).forEach(concept -> putBetterMatch(matches, concept.getConceptCode(), "title"));
        medicalConceptAliasMapper.selectList(new LambdaQueryWrapper<MedicalConceptAlias>()
                        .eq(MedicalConceptAlias::getStatus, "ACTIVE")
                        .like(MedicalConceptAlias::getAlias, token)
                        .last("LIMIT " + Math.max(limit, 20)))
                .forEach(alias -> putBetterMatch(matches, alias.getConceptCode(), "alias"));
        medicalSymptomMappingMapper.selectList(new LambdaQueryWrapper<MedicalSymptomMapping>()
                        .eq(MedicalSymptomMapping::getEnabled, true)
                        .and(wrapper -> wrapper.like(MedicalSymptomMapping::getSymptomTerm, token)
                                .or().like(MedicalSymptomMapping::getChiefComplaintTerm, token))
                        .last("LIMIT " + Math.max(limit, 20)))
                .forEach(mapping -> {
                    String matchType = StringUtils.hasText(mapping.getChiefComplaintTerm())
                            && mapping.getChiefComplaintTerm().contains(token) ? "chief_complaint" : "symptom";
                    putBetterMatch(matches, mapping.getConceptCode(), matchType);
                });
    }

    private List<String> findMatchedMappingTerms(String normalized, boolean chiefOnly) {
        Set<String> tokens = lookupTokens(normalized);
        if (tokens.isEmpty()) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : tokens) {
            LambdaQueryWrapper<MedicalSymptomMapping> wrapper = new LambdaQueryWrapper<MedicalSymptomMapping>()
                    .eq(MedicalSymptomMapping::getEnabled, true)
                    .last("LIMIT 30");
            if (chiefOnly) {
                wrapper.like(MedicalSymptomMapping::getChiefComplaintTerm, token);
            } else {
                wrapper.and(nested -> nested.like(MedicalSymptomMapping::getSymptomTerm, token)
                        .or().like(MedicalSymptomMapping::getChiefComplaintTerm, token));
            }
            medicalSymptomMappingMapper.selectList(wrapper).forEach(item -> {
                if (!chiefOnly && StringUtils.hasText(item.getSymptomTerm())) {
                    result.add(item.getSymptomTerm());
                }
                if (StringUtils.hasText(item.getChiefComplaintTerm())) {
                    result.add(item.getChiefComplaintTerm());
                }
            });
            if (result.size() >= 20) {
                break;
            }
        }
        return result.stream().limit(20).toList();
    }

    private Set<String> lookupTokens(String normalized) {
        Set<String> tokens = new LinkedHashSet<>();
        KnowledgeSearchLexicon.detectMedicalAnchors(normalized).forEach(tokens::add);
        KnowledgeSearchLexicon.extractMedicalRewriteHints(normalized).forEach(tokens::add);
        if (tokens.isEmpty() && normalized.length() <= 12) {
            tokens.add(normalized);
        }
        return tokens.stream()
                .filter(StringUtils::hasText)
                .filter(token -> token.length() >= 2 && token.length() <= 16)
                .limit(8)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private void clearLookupCaches() {
        diseaseMatchCache.clear();
        symptomMatchCache.clear();
        chiefComplaintMatchCache.clear();
    }

    private void putBetterMatch(Map<String, MatchInfo> matches, String conceptCode, String matchType) {
        if (!StringUtils.hasText(conceptCode)) {
            return;
        }
        MatchInfo candidate = new MatchInfo(matchType, scoreForMatchType(matchType));
        MatchInfo existing = matches.get(conceptCode);
        if (existing == null || candidate.score() > existing.score()) {
            matches.put(conceptCode, candidate);
        }
    }

    private LambdaQueryWrapper<MedicalSymptomMapping> uniqueMappingQuery(String conceptCode,
                                                                         String symptomTerm,
                                                                         String chiefComplaintTerm) {
        LambdaQueryWrapper<MedicalSymptomMapping> wrapper = new LambdaQueryWrapper<MedicalSymptomMapping>()
                .eq(MedicalSymptomMapping::getConceptCode, conceptCode);
        if (StringUtils.hasText(symptomTerm)) {
            wrapper.eq(MedicalSymptomMapping::getSymptomTerm, symptomTerm);
        } else {
            wrapper.isNull(MedicalSymptomMapping::getSymptomTerm);
        }
        if (StringUtils.hasText(chiefComplaintTerm)) {
            wrapper.eq(MedicalSymptomMapping::getChiefComplaintTerm, chiefComplaintTerm);
        } else {
            wrapper.isNull(MedicalSymptomMapping::getChiefComplaintTerm);
        }
        return wrapper.last("LIMIT 1");
    }

    private Map<String, MedicalConcept> conceptsByCode(List<String> conceptCodes) {
        if (conceptCodes == null || conceptCodes.isEmpty()) {
            return Map.of();
        }
        return medicalConceptMapper.selectList(new LambdaQueryWrapper<MedicalConcept>()
                        .in(MedicalConcept::getConceptCode, conceptCodes))
                .stream()
                .collect(java.util.stream.Collectors.toMap(MedicalConcept::getConceptCode, item -> item, (left, right) -> left));
    }

    private MedicalSymptomMappingItem toItem(MedicalSymptomMapping mapping, MedicalConcept entity) {
        String diseaseTitle = entity == null ? null : entity.getDiseaseName();
        return new MedicalSymptomMappingItem(
                mapping.getId(),
                mapping.getConceptCode(),
                entity == null ? null : entity.getStandardCode(),
                diseaseTitle,
                mapping.getSymptomTerm(),
                mapping.getChiefComplaintTerm(),
                mapping.getMappingType(),
                mapping.getWeight(),
                mapping.getSource(),
                Boolean.TRUE.equals(mapping.getEnabled())
        );
    }

    private record MatchInfo(String matchType, double score) {
    }

    private record CacheEntry<T>(T value, long expiresAt) {
        static <T> CacheEntry<T> of(T value) {
            return new CacheEntry<>(value, System.currentTimeMillis() + LOOKUP_CACHE_TTL_MS);
        }

        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
