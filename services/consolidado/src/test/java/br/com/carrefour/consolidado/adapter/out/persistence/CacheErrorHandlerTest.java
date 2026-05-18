package br.com.carrefour.consolidado.adapter.out.persistence;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheErrorHandlerTest {

    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    CacheConfig.RedisFallbackCacheErrorHandler handler;

    @BeforeEach
    void setup() {
        handler = new CacheConfig.RedisFallbackCacheErrorHandler(registry);
    }

    @Test
    void handleCacheGetError_deveIncrementarMetricaEAbsorverExcecao() {
        var cache = cacheNamed("saldo-consolidado");

        assertThatNoException().isThrownBy(() ->
            handler.handleCacheGetError(new RuntimeException("Redis fora"), cache, "2026-05-09"));

        assertThat(registry.counter("cache_redis_errors_total").count()).isEqualTo(1.0);
    }

    @Test
    void handleCachePutError_deveIncrementarMetricaEAbsorverExcecao() {
        var cache = cacheNamed("saldo-consolidado");

        assertThatNoException().isThrownBy(() ->
            handler.handleCachePutError(new RuntimeException("timeout"), cache, "key", "value"));

        assertThat(registry.counter("cache_redis_errors_total").count()).isEqualTo(1.0);
    }

    @Test
    void handleCacheEvictError_deveAbsorverExcecaoSemIncrementarMetrica() {
        var cache = cacheNamed("saldo-consolidado");

        assertThatNoException().isThrownBy(() ->
            handler.handleCacheEvictError(new RuntimeException("evict falhou"), cache, "key"));

        assertThat(registry.counter("cache_redis_errors_total").count()).isZero();
    }

    @Test
    void handleCacheClearError_deveAbsorverExcecaoSemIncrementarMetrica() {
        var cache = cacheNamed("saldo-consolidado");

        assertThatNoException().isThrownBy(() ->
            handler.handleCacheClearError(new RuntimeException("clear falhou"), cache));

        assertThat(registry.counter("cache_redis_errors_total").count()).isZero();
    }

    private Cache cacheNamed(String name) {
        var cache = mock(Cache.class);
        when(cache.getName()).thenReturn(name);
        return cache;
    }
}
