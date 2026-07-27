package com.gateway.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code providers} table.
 * Represents a configured AI provider (e.g., Ollama, OpenAI).
 */
@Entity
@Table(name = "providers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderConfig {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "is_default", nullable = false)
    private boolean defaultProvider;

    @Column(nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "provider", fetch = FetchType.LAZY)
    private List<ModelConfig> models;
}
