package com.linkjb.aimed.entity.vo.medical;

public record MedicalSymptomMappingItem(Long id,
                                        String conceptCode,
                                        String standardCode,
                                        String diseaseTitle,
                                        String symptomTerm,
                                        String chiefComplaintTerm,
                                        String mappingType,
                                        Double weight,
                                        String source,
                                        boolean enabled) {
}
