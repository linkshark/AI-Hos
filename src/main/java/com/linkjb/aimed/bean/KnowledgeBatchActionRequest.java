package com.linkjb.aimed.bean;

import java.util.ArrayList;
import java.util.List;

public class KnowledgeBatchActionRequest {
    private List<String> hashes = new ArrayList<>();

    public List<String> getHashes() {
        return hashes;
    }

    public void setHashes(List<String> hashes) {
        this.hashes = hashes == null ? new ArrayList<>() : hashes;
    }
}
