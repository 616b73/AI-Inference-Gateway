package com.gateway.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Writes metadata rows to the {@code request_logs} table after every inference call.
 * <p>
 * Called by {@link com.gateway.inference.InferenceService} in both success and failure paths.
 * No prompt/response bodies are stored (Rules.md §5 — privacy/size).
 */
@Service
public class RequestLogService {

    private static final Logger log = LoggerFactory.getLogger(RequestLogService.class);

    private final RequestLogRepository requestLogRepository;

    public RequestLogService(RequestLogRepository requestLogRepository) {
        this.requestLogRepository = requestLogRepository;
    }

    /**
     * Logs a single inference request outcome.
     *
     * @param requestId the unique request tracking ID (e.g., "req_...")
     * @param provider  the name of the provider that handled the request
     * @param model     the model name used for inference
     * @param status    "SUCCESS" or "FAILURE"
     * @param errorCode the {@link com.gateway.error.ErrorCode} name if failed, null if success
     * @param latencyMs end-to-end latency in milliseconds
     */
    public void log(String requestId, String provider, String model,
                    String status, String errorCode, int latencyMs) {
        RequestLog entry = RequestLog.builder()
                .id(UUID.randomUUID())
                .requestId(requestId)
                .timestamp(LocalDateTime.now())
                .provider(provider)
                .model(model)
                .status(status)
                .errorCode(errorCode)
                .latencyMs(latencyMs)
                .build();

        requestLogRepository.save(entry);
        log.debug("Logged request {} — provider={}, model={}, status={}, latencyMs={}",
                requestId, provider, model, status, latencyMs);
    }

    /**
     * Retrieve paginated and optionally filtered request logs, ordered by timestamp descending.
     *
     * @param page     zero-based page index
     * @param size     page size (capped at 100)
     * @param provider optional filter by provider name
     * @param status   optional filter by status ("SUCCESS" / "FAILURE")
     * @param from     optional inclusive start timestamp
     * @param to       optional inclusive end timestamp
     * @return a page of {@link RequestLog} entries
     */
    public Page<RequestLog> getLogs(int page, int size, String provider,
                                    String status, LocalDateTime from, LocalDateTime to) {
        int cappedSize = Math.min(size, 100);
        PageRequest pageRequest = PageRequest.of(page, cappedSize, Sort.by(Sort.Direction.DESC, "timestamp"));

        Specification<RequestLog> spec = Specification.where(null);

        if (provider != null && !provider.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("provider"), provider));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("timestamp"), to));
        }

        return requestLogRepository.findAll(spec, pageRequest);
    }
}
