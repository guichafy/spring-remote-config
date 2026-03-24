package guichafy.remote_config.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.ssm.SsmClient;

@Configuration
public class RedisSubscriberConfig {

    @Bean
    public RedisMessageListenerContainer redisListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisRefreshListener listener,
            ConfigRefreshProperties properties) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(properties.getRedisChannel()));
        return container;
    }

    @Bean
    @ConditionalOnMissingBean
    public SsmClient ssmClient() {
        return SsmClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
