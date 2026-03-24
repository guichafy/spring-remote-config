package guichafy.remote_config.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.SsmException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class SsmConfigPropertySource {

    private static final Logger log = LoggerFactory.getLogger(SsmConfigPropertySource.class);
    private static final String PROPERTY_SOURCE_NAME = "ssm-config";
    private static final String COMPONENT = "config-refresh";

    private final SsmClient ssmClient;
    private final ConfigurableEnvironment environment;
    private final ConfigRefreshProperties properties;
    private final Timer loadTimer;
    private final Counter errorCounter;

    private Map<String, Object> currentConfig = new HashMap<>();

    public SsmConfigPropertySource(SsmClient ssmClient,
                                   ConfigurableEnvironment environment,
                                   ConfigRefreshProperties properties,
                                   MeterRegistry meterRegistry) {
        this.ssmClient = ssmClient;
        this.environment = environment;
        this.properties = properties;
        this.loadTimer = Timer.builder("config.ssm.load.duration")
                .tag("app", environment.getProperty("spring.application.name", "unknown"))
                .register(meterRegistry);
        this.errorCounter = Counter.builder("config.ssm.load.errors")
                .tag("app", environment.getProperty("spring.application.name", "unknown"))
                .register(meterRegistry);
    }

    @PostConstruct
    public void loadInitialConfig() {
        setMdc();
        try {
            reloadFromSsm();
            log.info("Config loaded from SSM. Keys: {}", currentConfig.size());
        } catch (Exception e) {
            if (properties.isFallbackToLocal()) {
                log.warn("SSM unavailable, using local fallback. Error: {}", e.getMessage());
            } else {
                log.error("SSM unavailable and fallback disabled. Startup aborted.");
                throw e;
            }
        } finally {
            clearMdc();
        }
    }

    public Set<String> reloadFromSsm() {
        setMdc();
        try {
            return loadTimer.record(() -> {
                Map<String, Object> newConfig = new HashMap<>();
                String prefix = properties.getSsmPrefix();

                try {
                    String nextToken = null;
                    do {
                        GetParametersByPathRequest.Builder requestBuilder = GetParametersByPathRequest.builder()
                                .path(prefix)
                                .recursive(true)
                                .withDecryption(true);

                        if (nextToken != null) {
                            requestBuilder.nextToken(nextToken);
                        }

                        GetParametersByPathResponse response = ssmClient.getParametersByPath(requestBuilder.build());

                        for (Parameter param : response.parameters()) {
                            String key = convertToPropertyKey(param.name(), prefix);
                            newConfig.put(key, param.value());
                        }

                        nextToken = response.nextToken();
                    } while (nextToken != null);
                } catch (SsmException e) {
                    errorCounter.increment();
                    log.error("SSM GetParametersByPath failed. Prefix: {}. Error: {}", prefix, e.getMessage(), e);
                    throw e;
                }

                Set<String> changedKeys = detectChanges(currentConfig, newConfig);
                currentConfig = newConfig;
                registerPropertySource(newConfig);
                return changedKeys;
            });
        } finally {
            clearMdc();
        }
    }

    private String convertToPropertyKey(String parameterName, String prefix) {
        String stripped = parameterName.startsWith(prefix)
                ? parameterName.substring(prefix.length())
                : parameterName;
        return stripped.replace('/', '.');
    }

    private Set<String> detectChanges(Map<String, Object> oldConfig, Map<String, Object> newConfig) {
        Set<String> changedKeys = new HashSet<>();

        for (Map.Entry<String, Object> entry : newConfig.entrySet()) {
            Object oldValue = oldConfig.get(entry.getKey());
            if (oldValue == null || !oldValue.equals(entry.getValue())) {
                changedKeys.add(entry.getKey());
            }
        }

        for (String key : oldConfig.keySet()) {
            if (!newConfig.containsKey(key)) {
                changedKeys.add(key);
            }
        }

        return changedKeys;
    }

    private void registerPropertySource(Map<String, Object> config) {
        MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, new HashMap<>(config));
        MutablePropertySources propertySources = environment.getPropertySources();

        if (propertySources.contains(PROPERTY_SOURCE_NAME)) {
            propertySources.replace(PROPERTY_SOURCE_NAME, propertySource);
        } else {
            propertySources.addFirst(propertySource);
        }
    }

    private void setMdc() {
        MDC.put("component", COMPONENT);
        MDC.put("pod_name", System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local");
    }

    private void clearMdc() {
        MDC.remove("component");
        MDC.remove("pod_name");
    }
}
