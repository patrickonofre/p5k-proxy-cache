package com.p5k.proxycache.rules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findAllByEnabledTrue();

    Optional<Application> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
