package com.linkjb.aimed.bean;

import java.util.ArrayList;
import java.util.List;

public class KnowledgeUploadResponse {
    private int total;
    private int accepted;
    private int skipped;
    private int failed;
    private List<KnowledgeUploadItem> items = new ArrayList<>();

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getAccepted() {
        return accepted;
    }

    public void setAccepted(int accepted) {
        this.accepted = accepted;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public List<KnowledgeUploadItem> getItems() {
        return items;
    }

    public void setItems(List<KnowledgeUploadItem> items) {
        this.items = items;
    }
}
