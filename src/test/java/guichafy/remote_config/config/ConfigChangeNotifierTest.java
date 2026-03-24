package guichafy.remote_config.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConfigChangeNotifierTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private ConfigRefreshProperties properties;
    private ConfigChangeNotifier notifier;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new ConfigRefreshProperties();
        properties.setRedisChannel("config:refresh:test-app");
        notifier = new ConfigChangeNotifier(redisTemplate, properties);
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldPublishJsonToRedisChannel() throws Exception {
        notifier.notifyChange("ci-cd", List.of("feature.flag.enabled"), "Deployment update");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("config:refresh:test-app"), messageCaptor.capture());

        JsonNode json = objectMapper.readTree(messageCaptor.getValue());
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.get("source").asText()).isEqualTo("ci-cd");
        assertThat(json.get("application").asText()).isEqualTo("spring-remote-config");
        assertThat(json.get("changedKeys").isArray()).isTrue();
        assertThat(json.get("changedKeys").get(0).asText()).isEqualTo("feature.flag.enabled");
        assertThat(json.get("message").asText()).isEqualTo("Deployment update");
    }

    @Test
    void shouldIncludeTimestampAndSource() throws Exception {
        notifier.notifyChange("lambda", null, null);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("config:refresh:test-app"), messageCaptor.capture());

        JsonNode json = objectMapper.readTree(messageCaptor.getValue());
        assertThat(json.get("timestamp").asText()).isNotEmpty();
        assertThat(json.get("source").asText()).isEqualTo("lambda");
    }

    @Test
    void shouldHandleNullChangedKeys() throws Exception {
        notifier.notifyChange("manual", null, null);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("config:refresh:test-app"), messageCaptor.capture());

        JsonNode json = objectMapper.readTree(messageCaptor.getValue());
        assertThat(json.has("changedKeys")).isFalse();
        assertThat(json.has("message")).isFalse();
    }
}
