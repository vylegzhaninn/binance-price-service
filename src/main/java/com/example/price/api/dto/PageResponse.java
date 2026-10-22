package com.example.price.api.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable page contract independent of Spring's Page serialization format. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, boolean hasNext) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.hasNext());
    }
}
