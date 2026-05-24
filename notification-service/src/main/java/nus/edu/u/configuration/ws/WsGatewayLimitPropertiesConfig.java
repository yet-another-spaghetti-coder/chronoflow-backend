package nus.edu.u.configuration.ws;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "notification.ws")
public class WsGatewayLimitPropertiesConfig {
    private boolean enabled = true;
    private String baseUrl;
    private String rateKey = "rate:ws:global";
    private int rateLimit = 400;
    private Duration rateWindow = Duration.ofMinutes(1);
    private String internalServiceToken = "";

    private Timeouts timeouts = new Timeouts();
    private RetryProps retry = new RetryProps();

    @Data
    public static class Timeouts {
        int connectMs = 2000;
        int readMs = 3000;
        int writeMs = 1500;
    }

    @Data
    public static class RetryProps {
        int maxRetries = 2;
        int initialBackoffMs = 150;
    }
}
