package com.gateway.inference;

import com.gateway.error.GatewayException;
import com.gateway.logging.RequestLogService;
import com.gateway.routing.RoutingEngine;
import com.gateway.routing.RoutingEngine.RoutingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Core orchestration service implementing the full inference flow:
 * <ol>
 *   <li>Resolve provider via {@link RoutingEngine}</li>
 *   <li>Call provider adapter ({@link com.gateway.provider.AIProvider#infer})</li>
 *   <li>Stamp {@code requestId} and end-to-end latency onto the response</li>
 *   <li>Log the outcome (success or failure) to {@code request_logs}</li>
 * </ol>
 * This is the core value delivery of the MVP — wiring everything together.
 */
@Service
public class InferenceService {

    private static final Logger log = LoggerFactory.getLogger(InferenceService.class);

    private final RoutingEngine routingEngine;
    private final RequestLogService requestLogService;

    public InferenceService(RoutingEngine routingEngine, RequestLogService requestLogService) {
        this.routingEngine = routingEngine;
        this.requestLogService = requestLogService;
    }

    /**
     * Execute the full inference pipeline.
     *
     * @param request   the validated inference request
     * @param requestId the unique request tracking ID from {@link com.gateway.common.RequestIdFilter}
     * @return the enriched response with requestId and end-to-end latency
     * @throws GatewayException re-thrown after logging so GlobalExceptionHandler produces ApiError
     */
    public InferenceResponse infer(InferenceRequest request, String requestId) {
        long startTime = System.currentTimeMillis();

        // 1. Resolve provider via routing engine
        RoutingResult routing = routingEngine.resolve(request);

        log.info("Inference request {} → provider={}, model={}",
                requestId, routing.providerName(), request.getModel());

        try {
            // 2. Call provider adapter
            InferenceResponse providerResponse = routing.provider().infer(request);
            long latencyMs = System.currentTimeMillis() - startTime;

            // 3. Stamp requestId and end-to-end latency
            InferenceResponse enriched = InferenceResponse.builder()
                    .requestId(requestId)
                    .text(providerResponse.getText())
                    .model(providerResponse.getModel())
                    .provider(providerResponse.getProvider())
                    .latencyMs(latencyMs)
                    .build();

            // 4. Log success
            requestLogService.log(requestId, routing.providerName(), request.getModel(),
                    "SUCCESS", null, (int) latencyMs);

            log.info("Inference request {} completed in {}ms", requestId, latencyMs);
            return enriched;

        } catch (GatewayException ex) {
            long latencyMs = System.currentTimeMillis() - startTime;

            // 4. Log failure
            requestLogService.log(requestId, routing.providerName(), request.getModel(),
                    "FAILURE", ex.getErrorCode().name(), (int) latencyMs);

            log.warn("Inference request {} failed in {}ms: {} — {}",
                    requestId, latencyMs, ex.getErrorCode(), ex.getMessage());
            throw ex; // re-throw for GlobalExceptionHandler
        }
    }
}
