package pdfserver.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class WebAuthnChallengeStore {

    private static final String KEY_PREFIX = "webauthn:";
    private static final Duration TTL = Duration.ofSeconds(120);

    private final StringRedisTemplate redisTemplate;

    public WebAuthnChallengeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(String requestId, String serializedRequest) {
        String key = KEY_PREFIX + requestId;
        redisTemplate.opsForValue().set(key, serializedRequest, TTL);
    }

    /**
     * Atomically retrieves and removes challenge payload using Redis GETDEL.
     */
    public Optional<String> getAndRemove(String requestId) {
        String key = KEY_PREFIX + requestId;
        String val = redisTemplate.opsForValue().getAndDelete(key);
        return Optional.ofNullable(val);
    }
}

