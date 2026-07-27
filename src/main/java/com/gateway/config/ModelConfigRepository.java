package com.gateway.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfig, UUID> {

    List<ModelConfig> findByProviderIdAndActiveTrue(UUID providerId);
}
