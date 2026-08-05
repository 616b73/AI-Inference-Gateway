package com.gateway.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Generic wrapper for paginated API responses.
 * Used by {@code GET /v1/logs} and any future paginated endpoint.
 *
 * @param <T> the DTO type contained in the page
 */
@Getter
@Builder
@AllArgsConstructor
public class PaginatedResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
}
