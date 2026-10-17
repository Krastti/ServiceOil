package ru.krastti.config;

import java.time.Duration;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Локальный кеш готового справочника; изменения кеша применяются после commit. */
@Configuration(proxyBeanMethods = false)
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 100)
public class VehicleTemplateCacheConfiguration {
    @Bean
    CacheManager cacheManager(@Value("${serviceoil.cache.vehicle-templates-ttl-seconds:300}") long ttlSeconds) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("SERVICEOIL_VEHICLE_TEMPLATES_CACHE_TTL_SECONDS must be positive");
        }
        var manager = new CaffeineCacheManager("vehicle-templates");
        manager.setAllowNullValues(false);
        manager.setCaffeine(Caffeine.newBuilder().maximumSize(1).expireAfterWrite(Duration.ofSeconds(ttlSeconds)));
        return new TransactionAwareCacheManagerProxy(manager);
    }
}
