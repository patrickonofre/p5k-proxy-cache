package com.p5k.proxycache.config;

import com.p5k.proxycache.cache.CacheKeyFactory;
import com.p5k.proxycache.cache.CaffeineCacheRegistry;
import com.p5k.proxycache.proxy.CachingProxyFilter;
import com.p5k.proxycache.rules.ApplicationRegistry;
import com.p5k.proxycache.rules.RuleRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class ProxyFilterConfig {

    @Bean
    FilterRegistrationBean<CachingProxyFilter> cachingProxyFilter(
            ApplicationRegistry applications, RuleRegistry rules, CaffeineCacheRegistry caches,
            CacheKeyFactory keys) {
        FilterRegistrationBean<CachingProxyFilter> registration =
                new FilterRegistrationBean<>(new CachingProxyFilter(applications, rules, caches, keys));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
