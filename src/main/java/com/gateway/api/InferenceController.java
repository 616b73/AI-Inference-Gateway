package com.gateway.api;

import com.gateway.inference.InferenceRequest;
import com.gateway.inference.InferenceResponse;
import com.gateway.inference.InferenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the inference endpoint.
 * <p>
 * No business logic here — validates input and delegates to {@link InferenceService}
 * (Rules.md §3: "Controllers do not contain business logic").
 */
@RestController
@RequestMapping("/v1")
public class InferenceController {

    private final InferenceService inferenceService;

    public InferenceController(InferenceService inferenceService) {
        this.inferenceService = inferenceService;
    }

    /**
     * Primary inference endpoint — sends a prompt to an AI provider and returns the response.
     *
     * @param request     the inference request (validated via jakarta.validation)
     * @param httpRequest provides the requestId set by {@link com.gateway.common.RequestIdFilter}
     * @return 200 with the standardized {@link InferenceResponse}
     */
    @PostMapping("/inference")
    public ResponseEntity<InferenceResponse> infer(
            @Valid @RequestBody InferenceRequest request,
            HttpServletRequest httpRequest) {

        String requestId = (String) httpRequest.getAttribute("requestId");
        InferenceResponse response = inferenceService.infer(request, requestId);
        return ResponseEntity.ok(response);
    }
}
