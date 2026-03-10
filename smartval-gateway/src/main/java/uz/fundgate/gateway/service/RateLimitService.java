package uz.fundgate.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@ConditionalOnProperty(name = "fundgate.dev-mode", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    @Value("${fundgate.rate-limit.requests-per-minute:10}")
    private int requestsPerMinute;

    public boolean isRateLimited(String userId) {
        String key = "rate_limit:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        return count != null && count > requestsPerMinute;
    }

    public long getRemainingRequests(String userId) {
        String key = "rate_limit:" + userId;
        String val = redisTemplate.opsForValue().get(key);
        if (val == null) return requestsPerMinute;
        return Math.max(0, requestsPerMinute - Long.parseLong(val));
    }
}
