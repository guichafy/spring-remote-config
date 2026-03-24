package guichafy.remote_config.config;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/feature-flags")
public class FeatureFlagController {

    private static final String FEATURE_PREFIX = "feature.";

    private final ConfigurableEnvironment environment;

    public FeatureFlagController(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> getFeatureFlags() {
        Map<String, String> featureFlags = new LinkedHashMap<>();

        environment.getPropertySources().stream()
                .filter(EnumerablePropertySource.class::isInstance)
                .map(EnumerablePropertySource.class::cast)
                .flatMap(ps -> java.util.Arrays.stream(ps.getPropertyNames()))
                .filter(key -> key.startsWith(FEATURE_PREFIX))
                .distinct()
                .forEach(key -> featureFlags.put(key, environment.getProperty(key)));

        return ResponseEntity.ok(featureFlags);
    }
}
