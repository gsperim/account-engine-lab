package br.com.carrefour.consolidado.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

// CachingConfigurer garante que errorHandler() seja de fato usado pelo Spring Cache
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private final MeterRegistry           meterRegistry;
    private final RedisConnectionFactory  connectionFactory;
    private final ObjectMapper            objectMapper;

    public CacheConfig(MeterRegistry meterRegistry,
                       RedisConnectionFactory connectionFactory,
                       ObjectMapper objectMapper) {
        this.meterRegistry     = meterRegistry;
        this.connectionFactory = connectionFactory;
        this.objectMapper      = objectMapper;
    }

    @Bean
    @Override
    public CacheManager cacheManager() {
        var redisMapper = objectMapper.copy()
                .activateDefaultTyping(
                        objectMapper.getPolymorphicTypeValidator(),
                        DefaultTyping.NON_FINAL);

        var config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(redisMapper)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withCacheConfiguration("saldo-consolidado", config)
                .build();
    }

    // Registrado via CachingConfigurer — sem isso o Spring ignora o bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new RedisFallbackCacheErrorHandler(meterRegistry);
    }

    static class RedisFallbackCacheErrorHandler extends SimpleCacheErrorHandler {

        private static final Logger log = LoggerFactory.getLogger(RedisFallbackCacheErrorHandler.class);

        private final Counter errorsTotal;

        RedisFallbackCacheErrorHandler(MeterRegistry meterRegistry) {
            this.errorsTotal = Counter.builder("cache_redis_errors_total")
                    .description("Erros de acesso ao Redis — fallback ao banco ativado")
                    .register(meterRegistry);
        }

        @Override
        public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
            errorsTotal.increment();
            log.atWarn()
                    .addKeyValue("event", "cache_fallback_ativo")
                    .addKeyValue("cache", cache.getName())
                    .addKeyValue("key",   key)
                    .setCause(e)
                    .log("Falha no cache Redis — servindo do banco");
        }

        @Override
        public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
            errorsTotal.increment();
            log.atWarn()
                    .addKeyValue("event", "cache_put_error")
                    .addKeyValue("cache", cache.getName())
                    .addKeyValue("key",   key)
                    .setCause(e)
                    .log("Erro ao gravar no cache Redis");
        }

        @Override
        public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
            log.atWarn()
                    .addKeyValue("event", "cache_evict_error")
                    .addKeyValue("cache", cache.getName())
                    .addKeyValue("key",   key)
                    .setCause(e)
                    .log("Erro ao invalidar entrada no cache Redis");
        }

        @Override
        public void handleCacheClearError(RuntimeException e, Cache cache) {
            log.atWarn()
                    .addKeyValue("event", "cache_clear_error")
                    .addKeyValue("cache", cache.getName())
                    .setCause(e)
                    .log("Erro ao limpar cache Redis");
        }
    }
}
