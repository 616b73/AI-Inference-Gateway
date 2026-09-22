package com.gateway.inference;

import com.gateway.error.ErrorCode;
import com.gateway.error.GatewayException;
import com.gateway.logging.RequestLogService;
import com.gateway.routing.RoutingEngine;
import com.gateway.routing.RoutingEngine.RoutingResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

/** Owns one terminal diagnostic outcome for every request admitted to inference, including routing errors. */
@Service
public class InferenceService {
    private static final Logger log = LoggerFactory.getLogger(InferenceService.class);
    private final RoutingEngine routingEngine;
    private final RequestLogService requestLogService;
    private final MeterRegistry meters;

    public InferenceService(RoutingEngine routingEngine, RequestLogService requestLogService, MeterRegistry meters) {
        this.routingEngine = routingEngine; this.requestLogService = requestLogService; this.meters = meters;
    }

    public InferenceResponse infer(InferenceRequest request, String requestId) {
        long start = System.nanoTime();
        String provider = null;
        ErrorCode failure = null;
        try {
            RoutingResult routing = routingEngine.resolve(request);
            provider = routing.providerName();
            InferenceResponse result = routing.provider().infer(request);
            return InferenceResponse.builder().requestId(requestId).text(result.getText())
                    .model(result.getModel()).provider(result.getProvider()).latencyMs(elapsed(start)).build();
        } catch (GatewayException ex) {
            failure = ex.getErrorCode();
            throw ex;
        } catch (RuntimeException ex) {
            failure = ErrorCode.INTERNAL_ERROR;
            // Do not pass unknown exception messages into normal response or diagnostic logs.
            throw new GatewayException(failure, failure.safeMessage());
        } finally {
            String outcome = failure == null ? "SUCCESS" : "FAILURE";
            meters.counter("gateway.inference.outcomes", "outcome", outcome,
                    "error", failure == null ? "none" : failure.name()).increment();
            meters.timer("gateway.inference.duration", "outcome", outcome)
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            try {
                requestLogService.log(requestId, provider, request.getModel(), outcome,
                        failure == null ? null : failure.name(), (int) Math.min(Integer.MAX_VALUE, elapsed(start)));
            } catch (RuntimeException loggingFailure) {
                meters.counter("gateway.diagnostics.dropped").increment();
                log.warn("Diagnostic persistence failed requestId={}", requestId);
            }
        }
    }

    private long elapsed(long start) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start); }
}
