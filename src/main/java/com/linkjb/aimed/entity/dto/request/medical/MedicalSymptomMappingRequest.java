package com.linkjb.aimed.entity.dto.request.medical;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MedicalSymptomMappingRequest(Long id,
                                         @NotBlank String conceptCode,
                                         String symptomTerm,
                                         String chiefComplaintTerm,
                                         @NotBlank String mappingType,
                                         @NotNull @DecimalMin("0.0") Double weight,
                                         Boolean enabled) {
}
