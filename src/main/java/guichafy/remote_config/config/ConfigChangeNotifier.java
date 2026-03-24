package guichafy.remote_config.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// TODO: Secure this endpoint with Spring Security (e.g., internal network restriction or API key)
@RestController
@RequestMapping("/admin/config")
public class ConfigChangeNotifier {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangeNotifier.class);
    private static final String COMPONENT = "config-refresh";

    private final StringRedisTemplate redisTemplate;
    private final ConfigRefreshProperties properties;
    private final ObjectMapper objectMapper;

    public ConfigChangeNotifier(StringRedisTemplate redisTemplate,
                                ConfigRefreshProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/notify")
    public ResponseEntity<String> triggerRefresh(
            @RequestBody(required = false) NotifyRequest request) {

        List<String> changedKeys = request != null ? request.changedKeys() : null;
        String message = request != null ? request.message() : null;

        notifyChange("admin-api", changedKeys, message);
        return ResponseEntity.ok("Notification published to channel: " + properties.getRedisChannel());
    }

    public void notifyChange(String source, List<String> changedKeys, String message) {
        setMdc();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("timestamp", Instant.now().toString());
            payload.put("source", source);
            payload.put("application", "spring-remote-config");

            if (changedKeys != null) {
                payload.put("changedKeys", changedKeys);
            }
            if (message != null) {
                payload.put("message", message);
            }

            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.convertAndSend(properties.getRedisChannel(), json);

            log.info("Config change notification published. Channel: {}", properties.getRedisChannel());
        } catch (Exception e) {
            log.error("Failed to publish config change notification. Error: {}", e.getMessage(), e);
        } finally {
            clearMdc();
        }
    }

    public record NotifyRequest(
            List<String> changedKeys,
            String message
    ) {}

    private void setMdc() {
        MDC.put("component", COMPONENT);
        MDC.put("pod_name", System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local");
    }

    private void clearMdc() {
        MDC.remove("component");
        MDC.remove("pod_name");
    }
}
