package com.linkjb.aimed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linkjb.aimed.entity.dto.response.medical.MedicalKnowledgeMappingBackfillResponse;
import com.linkjb.aimed.entity.vo.medical.MedicalKnowledgeMappingItem;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalDocumentEntityMatch;
import com.linkjb.aimed.entity.dto.response.knowledge.retrieval.KnowledgeRetrievalMatchedDiseaseEntity;
import com.linkjb.aimed.entity.MedicalDocMapping;
import com.linkjb.aimed.entity.MedicalConcept;
import com.linkjb.aimed.entity.KnowledgeFileStatus;
import com.linkjb.aimed.mapper.MedicalDocMappingMapper;
import com.linkjb.aimed.mapper.MedicalConceptMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文档与疾病标准概念映射服务。
 */
@Service
public class MedicalKnowledgeMappingService {

    private final MedicalDocMappingMapper medicalStandardDocMappingMapper;
    private final MedicalConceptMapper medicalStandardEntityMapper;
    private final MedicalStandardLookupService medicalStandardLookupService;
    private final KnowledgeFileStatusService knowledgeFileStatusService;

    public MedicalKnowledgeMappingService(MedicalDocMappingMapper medicalStandardDocMappingMapper,
                                        MedicalConceptMapper medicalStandardEntityMapper,
                                        MedicalStandardLookupService medicalStandardLookupService,
                                        KnowledgeFileStatusService knowledgeFileStatusService) {
        this.medicalStandardDocMappingMapper = medicalStandardDocMappingMapper;
        this.medicalStandardEntityMapper = medicalStandardEntityMapper;
        this.medicalStandardLookupService = medicalStandardLookupService;
        this.knowledgeFileStatusService = knowledgeFileStatusService;
    }

    public MedicalKnowledgeMappingBackfillResponse backfill(List<String> hashes) {
        List<KnowledgeFileStatus> statuses = resolveStatuses(hashes);
        List<MedicalKnowledgeMappingItem> items = new ArrayList<>();
        int matchedCount = 0;
        int updatedCount = 0;
        for (KnowledgeFileStatus status : statuses) {
            List<KnowledgeRetrievalMatchedDiseaseEntity> matches = inferDiseaseMatches(status);
            if (!matches.isEmpty()) {
                matchedCount++;
            }
            for (KnowledgeRetrievalMatchedDiseaseEntity match : matches) {
                boolean updated = upsertMapping(status.getHash(), match.conceptCode(), "BACKFILL", match.score());
                if (updated) {
                    updatedCount++;
                }
                MedicalConcept entity = entityByUri(match.conceptCode());
                items.add(new MedicalKnowledgeMappingItem(
                        null,
                        status.getHash(),
                        match.conceptCode(),
                        entity == null ? match.standardCode() : entity.getStandardCode(),
                        entity == null ? firstNonBlank(match.diseaseName(), match.englishName()) : firstNonBlank(entity.getDiseaseName(), entity.getEnglishName()),
                        "BACKFILL",
                        match.score(),
                        0
                ));
            }
        }
        return new MedicalKnowledgeMappingBackfillResponse(statuses.size(), matchedCount, updatedCount, items);
    }

    public List<MedicalKnowledgeMappingItem> listMappings(String hash) {
        if (!StringUtils.hasText(hash)) {
            return List.of();
        }
        List<MedicalDocMapping> mappings = medicalStandardDocMappingMapper.selectList(new LambdaQueryWrapper<MedicalDocMapping>()
                .eq(MedicalDocMapping::getKnowledgeHash, hash)
                .orderByAsc(MedicalDocMapping::getSortOrder)
                .orderByDesc(MedicalDocMapping::getUpdatedAt));
        return mappings.stream().map(this::toItem).toList();
    }

    public List<MedicalKnowledgeMappingItem> replaceMappings(String hash, List<String> conceptCodes) {
        if (!StringUtils.hasText(hash)) {
            return List.of();
        }
        List<String> normalizedCodes = normalizeConceptCodes(conceptCodes);
        List<MedicalDocMapping> existingMappings = medicalStandardDocMappingMapper.selectList(new LambdaQueryWrapper<MedicalDocMapping>()
                .eq(MedicalDocMapping::getKnowledgeHash, hash));
        Map<String, MedicalDocMapping> existingByCode = new HashMap<>();
        existingMappings.forEach(item -> existingByCode.put(item.getConceptCode(), item));

        existingMappings.stream()
                .filter(item -> !normalizedCodes.contains(item.getConceptCode()))
                .map(MedicalDocMapping::getId)
                .filter(java.util.Objects::nonNull)
                .forEach(medicalStandardDocMappingMapper::deleteById);

        for (int index = 0; index < normalizedCodes.size(); index++) {
            String conceptCode = normalizedCodes.get(index);
            MedicalConcept concept = entityByUri(conceptCode);
            if (concept == null) {
                continue;
            }
            MedicalDocMapping existing = existingByCode.get(conceptCode);
            if (existing == null) {
                existing = new MedicalDocMapping();
                existing.setKnowledgeHash(hash);
                existing.setConceptCode(conceptCode);
                existing.setCreatedAt(LocalDateTime.now());
            }
            existing.setMatchSource("MANUAL");
            existing.setConfidence(1.0d);
            existing.setSortOrder(index + 1);
            existing.setUpdatedAt(LocalDateTime.now());
            if (existing.getId() == null) {
                medicalStandardDocMappingMapper.insert(existing);
            } else {
                medicalStandardDocMappingMapper.updateById(existing);
            }
        }
        return listMappings(hash);
    }

    public List<KnowledgeRetrievalDocumentEntityMatch> listDocumentMatches(List<String> knowledgeHashes) {
        if (knowledgeHashes == null || knowledgeHashes.isEmpty()) {
            return List.of();
        }
        List<MedicalDocMapping> mappings = medicalStandardDocMappingMapper.selectList(new LambdaQueryWrapper<MedicalDocMapping>()
                .in(MedicalDocMapping::getKnowledgeHash, knowledgeHashes));
        Map<String, MedicalConcept> conceptMap = conceptsByCode(mappings.stream()
                .map(MedicalDocMapping::getConceptCode)
                .filter(StringUtils::hasText)
                .toList());
        return mappings
                .stream()
                .map(mapping -> {
                    MedicalConcept entity = conceptMap.get(mapping.getConceptCode());
                    return new KnowledgeRetrievalDocumentEntityMatch(
                            mapping.getKnowledgeHash(),
                            mapping.getConceptCode(),
                            entity == null ? null : entity.getStandardCode(),
                            entity == null ? null : firstNonBlank(entity.getDiseaseName(), entity.getEnglishName()),
                            mapping.getMatchSource(),
                            mapping.getConfidence()
                    );
                })
                .toList();
    }

    public List<KnowledgeRetrievalMatchedDiseaseEntity> inferDiseaseMatches(KnowledgeFileStatus status) {
        if (status == null) {
            return List.of();
        }
        Set<String> candidates = new LinkedHashSet<>();
        if (StringUtils.hasText(status.getTitle())) {
            candidates.add(status.getTitle());
        }
        if (StringUtils.hasText(status.getOriginalFilename())) {
            candidates.add(status.getOriginalFilename());
        }
        if (StringUtils.hasText(status.getKeywords())) {
            candidates.addAll(List.of(status.getKeywords().split("[\\s,，;；]+")));
        }
        Map<String, KnowledgeRetrievalMatchedDiseaseEntity> matched = new LinkedHashMap<>();
        for (String candidate : candidates) {
            medicalStandardLookupService.matchDiseaseEntities(candidate, 4).forEach(item -> matched.putIfAbsent(item.conceptCode(), item));
        }
        return matched.values().stream().limit(4).toList();
    }

    private boolean upsertMapping(String knowledgeHash, String conceptCode, String source, Double confidence) {
        if (!StringUtils.hasText(knowledgeHash) || !StringUtils.hasText(conceptCode)) {
            return false;
        }
        MedicalDocMapping existing = medicalStandardDocMappingMapper.selectOne(new LambdaQueryWrapper<MedicalDocMapping>()
                .eq(MedicalDocMapping::getKnowledgeHash, knowledgeHash)
                .eq(MedicalDocMapping::getConceptCode, conceptCode)
                .last("LIMIT 1"));
        if (existing == null) {
            existing = new MedicalDocMapping();
            existing.setKnowledgeHash(knowledgeHash);
            existing.setConceptCode(conceptCode);
            existing.setCreatedAt(LocalDateTime.now());
            existing.setSortOrder(0);
        }
        existing.setMatchSource(source);
        existing.setConfidence(confidence);
        existing.setUpdatedAt(LocalDateTime.now());
        if (existing.getId() == null) {
            medicalStandardDocMappingMapper.insert(existing);
            return true;
        }
        medicalStandardDocMappingMapper.updateById(existing);
        return true;
    }

    private List<KnowledgeFileStatus> resolveStatuses(List<String> hashes) {
        if (hashes == null || hashes.isEmpty()) {
            return knowledgeFileStatusService.listAll();
        }
        return hashes.stream()
                .map(knowledgeFileStatusService::findByHash)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private MedicalKnowledgeMappingItem toItem(MedicalDocMapping mapping) {
        MedicalConcept entity = entityByUri(mapping.getConceptCode());
        return new MedicalKnowledgeMappingItem(
                mapping.getId(),
                mapping.getKnowledgeHash(),
                mapping.getConceptCode(),
                entity == null ? null : entity.getStandardCode(),
                entity == null ? null : firstNonBlank(entity.getDiseaseName(), entity.getEnglishName()),
                mapping.getMatchSource(),
                mapping.getConfidence(),
                mapping.getSortOrder()
        );
    }

    private MedicalConcept entityByUri(String conceptCode) {
        if (!StringUtils.hasText(conceptCode)) {
            return null;
        }
        return medicalStandardEntityMapper.selectOne(new LambdaQueryWrapper<MedicalConcept>()
                .eq(MedicalConcept::getConceptCode, conceptCode)
                .last("LIMIT 1"));
    }

    private Map<String, MedicalConcept> conceptsByCode(List<String> conceptCodes) {
        if (conceptCodes == null || conceptCodes.isEmpty()) {
            return Map.of();
        }
        return medicalStandardEntityMapper.selectList(new LambdaQueryWrapper<MedicalConcept>()
                        .in(MedicalConcept::getConceptCode, conceptCodes))
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        MedicalConcept::getConceptCode,
                        item -> item,
                        (left, right) -> left
                ));
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private List<String> normalizeConceptCodes(List<String> conceptCodes) {
        if (conceptCodes == null || conceptCodes.isEmpty()) {
            return List.of();
        }
        return conceptCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }
}
