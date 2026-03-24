package guichafy.remote_config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import guichafy.remote_config.config.ConfigRefreshProperties;

@SpringBootApplication
@EnableConfigurationProperties(ConfigRefreshProperties.class)
public class RemoteConfigApplication {

	public static void main(String[] args) {
		SpringApplication.run(RemoteConfigApplication.class, args);
	}

}
