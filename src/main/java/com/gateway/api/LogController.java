package com.gateway.api;

import com.gateway.api.dto.PaginatedResponse;
import com.gateway.api.dto.RequestLogDto;
import com.gateway.logging.RequestLog;
import com.gateway.logging.RequestLogService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing paginated request logs for auditing.
 * <p>
 * No business logic here — delegates pagination to {@link RequestLogService}
 * and maps JPA entities to {@link RequestLogDto} (Rules.md §3).
 */
@RestController
@RequestMapping("/v1")
public class LogController {

    private final RequestLogService requestLogService;

    public LogController(RequestLogService requestLogService) {
        this.requestLogService = requestLogService;
    }

    /**
     * Retrieve paginated request logs, ordered by most recent first.
     *
     * @param page zero-based page index (default 0)
     * @param size page size (default 20, max 100)
     * @return 200 with a {@link PaginatedResponse} of {@link RequestLogDto}
     */
    @GetMapping("/logs")
    public ResponseEntity<PaginatedResponse<RequestLogDto>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<RequestLog> logPage = requestLogService.getLogs(page, size);

        PaginatedResponse<RequestLogDto> response = PaginatedResponse.<RequestLogDto>builder()
                .content(logPage.getContent().stream().map(this::toDto).toList())
                .page(logPage.getNumber())
                .size(logPage.getSize())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .build();

        return ResponseEntity.ok(response);
    }

    private RequestLogDto toDto(RequestLog entity) {
        return RequestLogDto.builder()
                .requestId(entity.getRequestId())
                .timestamp(entity.getTimestamp())
                .provider(entity.getProvider())
                .model(entity.getModel())
                .status(entity.getStatus())
                .errorCode(entity.getErrorCode())
                .latencyMs(entity.getLatencyMs())
                .build();
    }
}
