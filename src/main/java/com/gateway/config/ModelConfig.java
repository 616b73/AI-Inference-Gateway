package com.gateway.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * JPA entity mapping to the {@code models} table.
 * Represents a model available through a specific provider (e.g., "qwen3" on Ollama).
 */
@Entity
@Table(name = "models", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"provider_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelConfig {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderConfig provider;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active;
}
