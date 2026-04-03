package com.linkjb.aimed.bean;

import java.util.List;

public record PagedResponse<T>(
        long total,
        int page,
        int size,
        List<T> items
) {
}
