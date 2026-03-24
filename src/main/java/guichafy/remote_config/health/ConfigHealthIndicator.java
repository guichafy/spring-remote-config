package guichafy.remote_config.health;

import guichafy.remote_config.config.ConfigRefreshProperties;
import guichafy.remote_config.config.RedisRefreshListener;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ConfigHealthIndicator implements HealthIndicator {

    private final RedisMessageListenerContainer listenerContainer;
    private final RedisRefreshListener refreshListener;
    private final ConfigRefreshProperties properties;

    public ConfigHealthIndicator(RedisMessageListenerContainer listenerContainer,
                                 RedisRefreshListener refreshListener,
                                 ConfigRefreshProperties properties) {
        this.listenerContainer = listenerContainer;
        this.refreshListener = refreshListener;
        this.properties = properties;
    }

    @Override
    public Health health() {
        Health.Builder builder;

        boolean containerRunning = listenerContainer.isRunning();
        boolean subscriptionActive = refreshListener.isSubscriptionActive();

        if (containerRunning && subscriptionActive) {
            builder = Health.up()
                    .withDetail("redisSubscription", "active");
        } else if (!containerRunning) {
            builder = Health.down()
                    .withDetail("redisSubscription", "disconnected");
        } else {
            builder = Health.unknown()
                    .withDetail("redisSubscription", "unknown");
        }

        Instant lastRefresh = refreshListener.getLastRefreshTimestamp();
        builder.withDetail("lastRefreshTimestamp", lastRefresh != null ? lastRefresh.toString() : "never")
                .withDetail("totalRefreshes", refreshListener.getTotalRefreshes())
                .withDetail("totalErrors", refreshListener.getTotalErrors())
                .withDetail("channel", properties.getRedisChannel());

        return builder.build();
    }
}
