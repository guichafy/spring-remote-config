package guichafy.remote_config.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.config")
public class ConfigRefreshProperties {

    private String ssmPrefix;
    private String redisChannel;
    private boolean fallbackToLocal = true;
    private long debounceWindowMs = 2000;

    public String getSsmPrefix() {
        return ssmPrefix;
    }

    public void setSsmPrefix(String ssmPrefix) {
        this.ssmPrefix = ssmPrefix;
    }

    public String getRedisChannel() {
        return redisChannel;
    }

    public void setRedisChannel(String redisChannel) {
        this.redisChannel = redisChannel;
    }

    public boolean isFallbackToLocal() {
        return fallbackToLocal;
    }

    public void setFallbackToLocal(boolean fallbackToLocal) {
        this.fallbackToLocal = fallbackToLocal;
    }

    public long getDebounceWindowMs() {
        return debounceWindowMs;
    }

    public void setDebounceWindowMs(long debounceWindowMs) {
        this.debounceWindowMs = debounceWindowMs;
    }
}
