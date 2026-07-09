package com.localcloud.orderservice.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Service
public class OrderCacheService {

    private static final String ORDER_CACHE_KEY_PREFIX = "order:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public OrderCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.cache.order-ttl-seconds:300}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<OrderResponse> get(Long id) {
        String json = redisTemplate.opsForValue().get(cacheKey(id));

        if (json == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(json, OrderResponse.class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to read order cache. id=" + id, exception);
        }
    }

    public void put(OrderResponse order) {
        try {
            String json = objectMapper.writeValueAsString(order);

            // TTL을 함께 지정해 캐시 데이터가 일정 시간이 지나면 자동 삭제되게 합니다.
            redisTemplate.opsForValue().set(cacheKey(order.id()), json, ttl);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to write order cache. id=" + order.id(), exception);
        }
    }

    private String cacheKey(Long id) {
        return ORDER_CACHE_KEY_PREFIX + id;
    }
}
