package pdfserver.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.io.Serializable;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSerializerTest {

    public record TestPayload(String id, String name, Instant createdAt) implements Serializable {}

    @Test
    void testRoundTripWithInstant() {
        GenericJacksonJsonRedisSerializer serializer = (GenericJacksonJsonRedisSerializer) RedisSerializer.json();

        Instant now = Instant.now();
        TestPayload original = new TestPayload("doc-123", "test document", now);

        byte[] raw = serializer.serialize(original);
        assertThat(raw).isNotNull();

        Object deserialized = serializer.deserialize(raw);
        assertThat(deserialized).isNotNull();
        assertThat(deserialized).isInstanceOf(TestPayload.class);

        TestPayload result = (TestPayload) deserialized;
        assertThat(result.id()).isEqualTo("doc-123");
        assertThat(result.name()).isEqualTo("test document");
        assertThat(result.createdAt()).isEqualTo(now);
    }
}
