package com.gateway.api;

import com.gateway.api.dto.ProviderDto;
import com.gateway.provider.AIProvider;
import com.gateway.provider.ProviderInfo;
import com.gateway.provider.ProviderRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing active providers and their models.
 * <p>
 * Reads from {@link ProviderRegistry}'s in-memory map — only providers
 * that successfully initialized at startup are returned.
 */
@RestController
@RequestMapping("/v1")
public class ProviderController {

    private final ProviderRegistry providerRegistry;

    public ProviderController(ProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    /**
     * List all active providers and their available models.
     *
     * @return 200 with a JSON array of {@link ProviderDto}
     */
    @GetMapping("/providers")
    public ResponseEntity<List<ProviderDto>> listProviders() {
        List<ProviderDto> providers = providerRegistry.getAllProviders().values().stream()
                .map(AIProvider::info)
                .map(this::toDto)
                .toList();

        return ResponseEntity.ok(providers);
    }

    private ProviderDto toDto(ProviderInfo info) {
        return ProviderDto.builder()
                .name(info.name())
                .type(info.type())
                .models(info.models())
                .build();
    }
}
