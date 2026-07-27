package com.gateway.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderConfigRepository extends JpaRepository<ProviderConfig, UUID> {

    Optional<ProviderConfig> findByName(String name);

    Optional<ProviderConfig> findByDefaultProviderTrueAndActiveTrue();
}
