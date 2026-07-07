package me.songha.concert.time;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class RedisTimeOverrideRepository implements TimeOverrideRepository {

    private static final String KEY = "reservation-service:admin:time-override";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Override
    public TimeOverrideSettings get() {
        String value = redisTemplate.opsForValue().get(KEY);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, TimeOverrideSettings.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read time override config.", exception);
        }
    }

    @Override
    public void save(TimeOverrideSettings config) {
        try {
            redisTemplate.opsForValue().set(KEY, objectMapper.writeValueAsString(config), TTL);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to save time override config.", exception);
        }
    }

    @Override
    public void reset() {
        redisTemplate.delete(KEY);
    }
}
