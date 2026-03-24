package guichafy.remote_config.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class RedisRefreshListener implements MessageListener, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RedisRefreshListener.class);
    private static final String COMPONENT = "config-refresh";

    private final SsmConfigPropertySource ssmConfigPropertySource;
    private final ContextRefresher contextRefresher;
    private final ConfigRefreshProperties properties;
    private final ObjectMapper objectMapper;

    private final Timer refreshTimer;
    private final Counter refreshCounter;
    private final Counter errorCounter;
    private final AtomicLong keysChangedGauge;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile ScheduledFuture<?> pendingRefresh;
    private volatile Instant lastRefreshTimestamp;
    private final AtomicBoolean subscriptionActive = new AtomicBoolean(true);
    private final AtomicLong totalRefreshes = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);

    public RedisRefreshListener(SsmConfigPropertySource ssmConfigPropertySource,
                                ContextRefresher contextRefresher,
                                ConfigRefreshProperties properties,
                                MeterRegistry meterRegistry) {
        this.ssmConfigPropertySource = ssmConfigPropertySource;
        this.contextRefresher = contextRefresher;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();

        String appName = "spring-remote-config";
        String podName = System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local";

        this.refreshTimer = Timer.builder("config.refresh.duration")
                .tag("app", appName)
                .register(meterRegistry);
        this.refreshCounter = Counter.builder("config.refresh.total")
                .tag("app", appName)
                .tag("pod", podName)
                .register(meterRegistry);
        this.errorCounter = Counter.builder("config.refresh.errors")
                .tag("app", appName)
                .tag("pod", podName)
                .register(meterRegistry);
        this.keysChangedGauge = new AtomicLong(0);
        meterRegistry.gauge("config.refresh.keys.changed", keysChangedGauge);
        meterRegistry.gauge("config.redis.subscription.active",
                subscriptionActive, ab -> ab.get() ? 1.0 : 0.0);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        onMessage(body);
    }

    public void onMessage(String message) {
        setMdc();
        try {
            String source = parseSource(message);
            log.debug("Config refresh notification received. Source: {}", source);

            synchronized (this) {
                if (pendingRefresh != null) {
                    pendingRefresh.cancel(false);
                    log.debug("Refresh rescheduled due to debounce. Window: {}ms", properties.getDebounceWindowMs());
                }
                pendingRefresh = scheduler.schedule(
                        () -> executeRefresh(source),
                        properties.getDebounceWindowMs(),
                        TimeUnit.MILLISECONDS
                );
            }
        } catch (Exception e) {
            log.warn("Invalid message received on config channel: {}", message);
        } finally {
            clearMdc();
        }
    }

    private void executeRefresh(String source) {
        setMdc();
        try {
            if (!refreshLock.tryLock()) {
                log.warn("Refresh skipped, another refresh in progress");
                return;
            }
            try {
                log.info("Config refresh started. Trigger: {}", source);
                refreshTimer.record(() -> {
                    try {
                        Set<String> changedKeys = ssmConfigPropertySource.reloadFromSsm();
                        contextRefresher.refresh();

                        refreshCounter.increment();
                        totalRefreshes.incrementAndGet();
                        keysChangedGauge.set(changedKeys.size());
                        lastRefreshTimestamp = Instant.now();

                        log.info("Config refresh completed. Duration: recorded by timer. Keys changed: {}",
                                changedKeys.size());
                    } catch (Exception e) {
                        errorCounter.increment();
                        totalErrors.incrementAndGet();
                        log.error("Config refresh failed. Error: {}", e.getMessage(), e);
                    }
                });
            } finally {
                refreshLock.unlock();
            }
        } finally {
            clearMdc();
        }
    }

    private String parseSource(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            if (node.has("source")) {
                return node.get("source").asText();
            }
        } catch (JsonProcessingException e) {
            // will be caught in onMessage
        }
        return "unknown";
    }

    public Instant getLastRefreshTimestamp() {
        return lastRefreshTimestamp;
    }

    public boolean isSubscriptionActive() {
        return subscriptionActive.get();
    }

    public void setSubscriptionActive(boolean active) {
        this.subscriptionActive.set(active);
    }

    public long getTotalRefreshes() {
        return totalRefreshes.get();
    }

    public long getTotalErrors() {
        return totalErrors.get();
    }

    @Override
    public void destroy() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
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
