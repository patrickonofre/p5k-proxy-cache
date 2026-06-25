package com.p5k.proxycache.rules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CacheRuleRepository extends JpaRepository<CacheRule, Long> {

    List<CacheRule> findAllByEnabledTrue();

    List<CacheRule> findByApplicationId(Long applicationId);
}
