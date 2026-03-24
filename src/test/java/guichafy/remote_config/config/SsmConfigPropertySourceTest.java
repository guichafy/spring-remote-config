package guichafy.remote_config.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.mock.env.MockEnvironment;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.SsmException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SsmConfigPropertySourceTest {

    @Mock
    private SsmClient ssmClient;

    private ConfigurableEnvironment environment;
    private ConfigRefreshProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private SsmConfigPropertySource propertySource;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment().withProperty("spring.application.name", "test-app");
        properties = new ConfigRefreshProperties();
        properties.setSsmPrefix("/config/test-app/");
        properties.setRedisChannel("config:refresh:test-app");
        properties.setFallbackToLocal(true);
        meterRegistry = new SimpleMeterRegistry();
        propertySource = new SsmConfigPropertySource(ssmClient, environment, properties, meterRegistry);
    }

    @Test
    void shouldLoadParametersFromSsm() {
        GetParametersByPathResponse response = GetParametersByPathResponse.builder()
                .parameters(
                        Parameter.builder().name("/config/test-app/db.url").value("jdbc:mysql://localhost").build(),
                        Parameter.builder().name("/config/test-app/db.username").value("admin").build(),
                        Parameter.builder().name("/config/test-app/feature.enabled").value("true").build()
                )
                .build();
        when(ssmClient.getParametersByPath(any(GetParametersByPathRequest.class))).thenReturn(response);

        propertySource.loadInitialConfig();

        assertThat(environment.getProperty("db.url")).isEqualTo("jdbc:mysql://localhost");
        assertThat(environment.getProperty("db.username")).isEqualTo("admin");
        assertThat(environment.getProperty("feature.enabled")).isEqualTo("true");
    }

    @Test
    void shouldHandlePaginatedResults() {
        GetParametersByPathResponse page1 = GetParametersByPathResponse.builder()
                .parameters(
                        Parameter.builder().name("/config/test-app/key1").value("value1").build(),
                        Parameter.builder().name("/config/test-app/key2").value("value2").build()
                )
                .nextToken("token-page-2")
                .build();

        GetParametersByPathResponse page2 = GetParametersByPathResponse.builder()
                .parameters(
                        Parameter.builder().name("/config/test-app/key3").value("value3").build()
                )
                .build();

        when(ssmClient.getParametersByPath(any(GetParametersByPathRequest.class)))
                .thenReturn(page1)
                .thenReturn(page2);

        propertySource.loadInitialConfig();

        assertThat(environment.getProperty("key1")).isEqualTo("value1");
        assertThat(environment.getProperty("key2")).isEqualTo("value2");
        assertThat(environment.getProperty("key3")).isEqualTo("value3");
    }

    @Test
    void shouldFallbackOnSsmFailure() {
        properties.setFallbackToLocal(true);
        when(ssmClient.getParametersByPath(any(GetParametersByPathRequest.class)))
                .thenThrow(SsmException.builder().message("Connection refused").build());

        assertThatNoException().isThrownBy(() -> propertySource.loadInitialConfig());

        MutablePropertySources sources = environment.getPropertySources();
        assertThat(sources.contains("ssm-config")).isFalse();
    }

    @Test
    void shouldDetectChangedKeys() {
        GetParametersByPathResponse initialResponse = GetParametersByPathResponse.builder()
                .parameters(
                        Parameter.builder().name("/config/test-app/key1").value("value1").build(),
                        Parameter.builder().name("/config/test-app/key2").value("value2").build()
                )
                .build();
        when(ssmClient.getParametersByPath(any(GetParametersByPathRequest.class))).thenReturn(initialResponse);

        propertySource.loadInitialConfig();

        GetParametersByPathResponse updatedResponse = GetParametersByPathResponse.builder()
                .parameters(
                        Parameter.builder().name("/config/test-app/key1").value("value1").build(),
                        Parameter.builder().name("/config/test-app/key2").value("new-value2").build()
                )
                .build();
        when(ssmClient.getParametersByPath(any(GetParametersByPathRequest.class))).thenReturn(updatedResponse);

        Set<String> changedKeys = propertySource.reloadFromSsm();

        assertThat(changedKeys).containsExactly("key2");
    }

    @Test
    void shouldStripPrefixAndConvertSlashesToDots() {
        GetParametersByPathResponse response = GetParametersByPathResponse.builder()
                .parameters(
                        Parameter.builder().name("/config/test-app/feature/flag/enabled").value("true").build()
                )
                .build();
        when(ssmClient.getParametersByPath(any(GetParametersByPathRequest.class))).thenReturn(response);

        propertySource.loadInitialConfig();

        assertThat(environment.getProperty("feature.flag.enabled")).isEqualTo("true");
    }
}
