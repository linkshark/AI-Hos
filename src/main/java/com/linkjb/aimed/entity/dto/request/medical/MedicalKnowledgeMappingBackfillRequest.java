package com.linkjb.aimed.entity.dto.request.medical;

import java.util.List;

public class MedicalKnowledgeMappingBackfillRequest {

    private List<String> hashes;

    public List<String> getHashes() {
        return hashes;
    }

    public void setHashes(List<String> hashes) {
        this.hashes = hashes;
    }
}
