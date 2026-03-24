package guichafy.remote_config.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ConfigRefreshEventListener {

    private static final Logger log = LoggerFactory.getLogger(ConfigRefreshEventListener.class);
    private static final String COMPONENT = "config-refresh";

    @EventListener(RefreshScopeRefreshedEvent.class)
    public void onRefresh(RefreshScopeRefreshedEvent event) {
        MDC.put("component", COMPONENT);
        MDC.put("pod_name", System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local");
        try {
            log.info("Configuration refresh completed. RefreshScope beans rebuilt. Event: {}",
                    event.getName());
            // Extension point: teams can add custom logic here
            // Examples:
            //   - Cache invalidation
            //   - Connection pool reconnection
            //   - Circuit breaker reset
        } finally {
            MDC.remove("component");
            MDC.remove("pod_name");
        }
    }
}
