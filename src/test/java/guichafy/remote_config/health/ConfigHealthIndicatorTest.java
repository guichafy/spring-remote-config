package guichafy.remote_config.health;

import guichafy.remote_config.config.ConfigRefreshProperties;
import guichafy.remote_config.config.RedisRefreshListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigHealthIndicatorTest {

    @Mock
    private RedisMessageListenerContainer listenerContainer;

    @Mock
    private RedisRefreshListener refreshListener;

    private ConfigRefreshProperties properties;
    private ConfigHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        properties = new ConfigRefreshProperties();
        properties.setRedisChannel("config:refresh:test-app");
        healthIndicator = new ConfigHealthIndicator(listenerContainer, refreshListener, properties);
    }

    @Test
    void shouldReportUpWhenSubscribed() {
        when(listenerContainer.isRunning()).thenReturn(true);
        when(refreshListener.isSubscriptionActive()).thenReturn(true);
        when(refreshListener.getTotalRefreshes()).thenReturn(5L);
        when(refreshListener.getTotalErrors()).thenReturn(0L);
        when(refreshListener.getLastRefreshTimestamp()).thenReturn(Instant.parse("2026-03-23T14:30:00Z"));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("redisSubscription")).isEqualTo("active");
        assertThat(health.getDetails().get("totalRefreshes")).isEqualTo(5L);
        assertThat(health.getDetails().get("totalErrors")).isEqualTo(0L);
    }

    @Test
    void shouldReportDownWhenDisconnected() {
        when(listenerContainer.isRunning()).thenReturn(false);
        when(refreshListener.getTotalRefreshes()).thenReturn(0L);
        when(refreshListener.getTotalErrors()).thenReturn(0L);
        when(refreshListener.getLastRefreshTimestamp()).thenReturn(null);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("redisSubscription")).isEqualTo("disconnected");
    }

    @Test
    void shouldIncludeLastRefreshTimestamp() {
        when(listenerContainer.isRunning()).thenReturn(true);
        when(refreshListener.isSubscriptionActive()).thenReturn(true);
        when(refreshListener.getTotalRefreshes()).thenReturn(3L);
        when(refreshListener.getTotalErrors()).thenReturn(1L);

        Instant timestamp = Instant.parse("2026-03-23T10:15:30Z");
        when(refreshListener.getLastRefreshTimestamp()).thenReturn(timestamp);

        Health health = healthIndicator.health();

        assertThat(health.getDetails().get("lastRefreshTimestamp")).isEqualTo(timestamp.toString());
        assertThat(health.getDetails().get("channel")).isEqualTo("config:refresh:test-app");
    }
}
