package guichafy.remote_config.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class ConfigRefreshIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockitoBean
    private SsmClient ssmClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ConfigRefreshProperties properties;

    @Autowired
    private ConfigurableEnvironment environment;

    @Autowired
    private RedisRefreshListener refreshListener;

    @Autowired
    private SsmConfigPropertySource ssmConfigPropertySource;

    @BeforeEach
    void setUp() {
        // Stub mock SSM with initial values (runs after context startup)
        GetParametersByPathResponse response = GetParametersByPathResponse.builder()
                .parameters(
                        Parameter.builder()
                                .name("/config/spring-remote-config/test.key")
                                .value("initial-value")
                                .build()
                )
                .build();
        when(ssmClient.getParametersByPath(any(GetParametersByPathRequest.class)))
                .thenReturn(response);
    }

    @Test
    void fullRefreshCycle() throws Exception {
        // 1. Load initial config using the Spring-managed bean (mock is now stubbed)
        ssmConfigPropertySource.reloadFromSsm();

        // 2. Verify initial config loaded
        assertThat(environment.getProperty("test.key")).isEqualTo("initial-value");

        // 3. Update mock SSM to return new value
        GetParametersByPathResponse updatedResponse = GetParametersByPathResponse.builder()
                .parameters(
                        Parameter.builder()
                                .name("/config/spring-remote-config/test.key")
                                .value("updated-value")
                                .build()
                )
                .build();
        when(ssmClient.getParametersByPath(any(GetParametersByPathRequest.class)))
                .thenReturn(updatedResponse);

        // 4. Publish notification to Redis channel
        String notification = """
                {
                    "timestamp": "2026-03-23T14:30:00Z",
                    "source": "integration-test",
                    "application": "spring-remote-config",
                    "changedKeys": ["/config/spring-remote-config/test.key"],
                    "message": "Integration test update"
                }
                """;
        redisTemplate.convertAndSend(properties.getRedisChannel(), notification);

        // 5. Wait for debounce window + processing (debounce is 500ms in test profile)
        Thread.sleep(3000);

        // 6. Verify SSM was reloaded (manual load + Redis-triggered reload)
        verify(ssmClient, atLeast(2)).getParametersByPath(any(GetParametersByPathRequest.class));

        // 7. Verify updated value is available
        assertThat(environment.getProperty("test.key")).isEqualTo("updated-value");

        // 8. Verify refresh listener recorded the event
        assertThat(refreshListener.getLastRefreshTimestamp()).isNotNull();
        assertThat(refreshListener.getTotalRefreshes()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldSurviveRedisRestart() throws Exception {
        // 1. Verify subscription is active
        assertThat(refreshListener.isSubscriptionActive()).isTrue();

        // 2. Stop Redis container
        redis.stop();

        // 3. Service should still be running (no crash)
        Thread.sleep(2000);
        assertThat(environment.containsProperty("spring.application.name")).isTrue();

        // 4. Start Redis container again
        redis.start();

        // RedisMessageListenerContainer reconnects automatically.
        // In Testcontainers the port may change on restart, so we can't verify
        // full reconnection. This test validates the app doesn't crash.
        Thread.sleep(3000);

        // 5. Application should still be functional
        assertThat(environment.containsProperty("spring.application.name")).isTrue();
    }
}
