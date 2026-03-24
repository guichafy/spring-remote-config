package guichafy.remote_config.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.context.refresh.ContextRefresher;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisRefreshListenerTest {

    @Mock
    private SsmConfigPropertySource ssmConfigPropertySource;

    @Mock
    private ContextRefresher contextRefresher;

    private SimpleMeterRegistry meterRegistry;
    private ConfigRefreshProperties properties;
    private RedisRefreshListener listener;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new ConfigRefreshProperties();
        properties.setDebounceWindowMs(100); // short debounce for tests
        properties.setRedisChannel("config:refresh:test");
        listener = new RedisRefreshListener(ssmConfigPropertySource, contextRefresher, properties, meterRegistry);
    }

    @Test
    void shouldTriggerRefreshOnValidMessage() throws Exception {
        when(ssmConfigPropertySource.reloadFromSsm()).thenReturn(Set.of("key1"));
        when(contextRefresher.refresh()).thenReturn(Set.of("key1"));

        String message = """
                {"timestamp":"2026-03-23T14:30:00Z","source":"test","application":"test-app"}
                """;

        listener.onMessage(message);

        // Wait for debounce window to expire + execution
        Thread.sleep(300);

        verify(ssmConfigPropertySource).reloadFromSsm();
        verify(contextRefresher).refresh();
    }

    @Test
    void shouldDebounceMultipleMessages() throws Exception {
        when(ssmConfigPropertySource.reloadFromSsm()).thenReturn(Set.of("key1"));
        when(contextRefresher.refresh()).thenReturn(Set.of("key1"));

        String message = """
                {"timestamp":"2026-03-23T14:30:00Z","source":"test","application":"test-app"}
                """;

        // Send 5 messages rapidly
        for (int i = 0; i < 5; i++) {
            listener.onMessage(message);
            Thread.sleep(20); // 20ms between messages, within debounce window
        }

        // Wait for debounce to expire
        Thread.sleep(300);

        // Should have refreshed exactly once
        verify(ssmConfigPropertySource, times(1)).reloadFromSsm();
        verify(contextRefresher, times(1)).refresh();
    }

    @Test
    void shouldHandleInvalidJson() {
        assertThatNoException().isThrownBy(() -> listener.onMessage("not json at all"));

        // No refresh should be triggered for invalid JSON
        verifyNoInteractions(ssmConfigPropertySource);
        verifyNoInteractions(contextRefresher);
    }

    @Test
    void shouldNotCrashOnRefreshError() throws Exception {
        when(ssmConfigPropertySource.reloadFromSsm()).thenThrow(new RuntimeException("SSM error"));

        String message = """
                {"timestamp":"2026-03-23T14:30:00Z","source":"test","application":"test-app"}
                """;

        listener.onMessage(message);
        Thread.sleep(300);

        // Listener should still be functional
        assertThat(listener.isSubscriptionActive()).isTrue();

        // Error counter should be incremented
        assertThat(listener.getTotalErrors()).isEqualTo(1);
    }

    @Test
    void shouldSkipWhenRefreshLocked() throws Exception {
        // Acquire the lock externally to simulate another refresh in progress
        Field lockField = RedisRefreshListener.class.getDeclaredField("refreshLock");
        lockField.setAccessible(true);
        ReentrantLock lock = (ReentrantLock) lockField.get(listener);

        // Hold the lock from the test thread
        lock.lock();
        try {
            properties.setDebounceWindowMs(10);
            listener = new RedisRefreshListener(ssmConfigPropertySource, contextRefresher, properties, meterRegistry);

            // Re-acquire the lock on the new listener
            Field newLockField = RedisRefreshListener.class.getDeclaredField("refreshLock");
            newLockField.setAccessible(true);
            ReentrantLock newLock = (ReentrantLock) newLockField.get(listener);
            newLock.lock();

            try {
                String message = """
                        {"timestamp":"2026-03-23T14:30:00Z","source":"test","application":"test-app"}
                        """;

                listener.onMessage(message);

                // Wait for debounce to fire and the scheduled task to attempt lock acquisition
                Thread.sleep(200);

                // reloadFromSsm should NOT have been called because lock was held
                verify(ssmConfigPropertySource, never()).reloadFromSsm();
            } finally {
                newLock.unlock();
            }
        } finally {
            lock.unlock();
        }
    }

    @Test
    void shouldRecordMetrics() throws Exception {
        when(ssmConfigPropertySource.reloadFromSsm()).thenReturn(Set.of("key1", "key2"));
        when(contextRefresher.refresh()).thenReturn(Set.of("key1", "key2"));

        String message = """
                {"timestamp":"2026-03-23T14:30:00Z","source":"test","application":"test-app"}
                """;

        listener.onMessage(message);
        Thread.sleep(300);

        assertThat(meterRegistry.find("config.refresh.total").counter()).isNotNull();
        assertThat(meterRegistry.find("config.refresh.total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.find("config.refresh.duration").timer()).isNotNull();
        assertThat(meterRegistry.find("config.refresh.duration").timer().count()).isEqualTo(1);
        assertThat(listener.getLastRefreshTimestamp()).isNotNull();
        assertThat(listener.getTotalRefreshes()).isEqualTo(1);
    }
}
