package nus.edu.u.task.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.shared.rpc.notification.dto.common.NotificationRequestDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Publishes NotificationRequestDTO messages to a fixed Pub/Sub topic. Used by other microservices
 * to trigger notifications via the Notification Service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private static final String TOPIC_NAME = "chronoflow-notification";

    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    /** Publishes a NotificationRequestDTO to the fixed Pub/Sub topic. */
    public String publish(NotificationRequestDTO req) {
        validate(req);

        try {
            // Serialize to JSON
            String payload = objectMapper.writeValueAsString(req);

            // Add basic message attributes for observability
            Map<String, String> attrs = new HashMap<>();
            put(attrs, "eventId", req.getEventId());
            put(attrs, "channel", req.getChannel() != null ? req.getChannel().name() : null);
            put(attrs, "type", req.getType() != null ? req.getType().name() : null);
            put(attrs, "userId", req.getUserId());
            put(attrs, "to", req.getTo());

            String messageId =
                    pubSubTemplate.publish(TOPIC_NAME, payload, attrs).get(10, TimeUnit.SECONDS);
            log.info(
                    "📤 Published Notification accepted topic={} msgId={} eventId={} channel={} type={}",
                    TOPIC_NAME,
                    messageId,
                    req.getEventId(),
                    req.getChannel(),
                    req.getType());

            return messageId;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(
                    "Interrupted while publishing notification eventId={} channel={} type={}",
                    req.getEventId(),
                    req.getChannel(),
                    req.getType(),
                    e);
            throw new RuntimeException("Pub/Sub publish interrupted", e);
        } catch (Exception e) {
            log.error(
                    "Failed to publish notification eventId={} channel={} type={}",
                    req.getEventId(),
                    req.getChannel(),
                    req.getType(),
                    e);
            throw new RuntimeException("Pub/Sub publish failed", e);
        }
    }

    // simple validation to avoid malformed events
    private static void validate(NotificationRequestDTO req) {
        if (!StringUtils.hasText(req.getEventId())) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (req.getChannel() == null) {
            throw new IllegalArgumentException("channel is required");
        }
    }

    private static void put(Map<String, String> map, String key, String value) {
        if (StringUtils.hasText(value)) {
            map.put(key, value);
        }
    }
}
