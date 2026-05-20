package nus.edu.u.core.push;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.core.common.NotificationSender;
import nus.edu.u.domain.dataObject.common.NotificationEventDO;
import nus.edu.u.domain.dto.common.NotificationRequestDTO;
import nus.edu.u.domain.dto.push.PushRequestDTO;
import nus.edu.u.enums.common.NotificationChannel;
import nus.edu.u.services.common.NotificationEventService;
import nus.edu.u.services.push.PushService;
import org.springframework.stereotype.Component;

/**
 * PUSH sender: 1) Persist notification_event record (server-side inbox) 2) Send minimal push
 * payload to devices: notifId + type (+ optional deepLink)
 *
 * <p>No template rendering and no sensitive payload in FCM.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushNotificationSender implements NotificationSender {

    private final PushService pushService;
    private final NotificationEventService notificationEventService;

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.PUSH;
    }

    @Override
    public String send(NotificationRequestDTO request) {
        // Validate new model fields
        if (request.getRecipientUserId() == null || request.getRecipientUserId().isBlank()) {
            throw new IllegalArgumentException("recipientUserId is required for push");
        }
        if (request.getType() == null) {
            throw new IllegalArgumentException("type is required for push");
        }
        if (request.getEventId() == null || request.getEventId().isBlank()) {
            throw new IllegalArgumentException("eventId is required for push");
        }

        // 1) Create notification event row (source of truth)
        NotificationEventDO notif = notificationEventService.createFromRequest(request);
        final String notifId = notif.getId();

        // 2) Build minimal push payload (safe identifiers)
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("notifId", notifId);
        // optional hint:
        data.put("type", request.getType().name());

        // 3) Send push (title/body should be non-sensitive)
        PushRequestDTO base =
                PushRequestDTO.builder()
                        .eventId(request.getEventId())
                        .type(request.getType())
                        .title("Notification")
                        .body(
                                request.getPreviewText() != null
                                        ? request.getPreviewText()
                                        : "You have a new notification")
                        .data(data)
                        .build();

        pushService.sendToUser(request.getRecipientUserId(), base);

        log.info(
                "Push initiated: notifId={}, recipientUserId={}, eventId={}",
                notifId,
                request.getRecipientUserId(),
                request.getEventId());

        return "ACCEPTED";
    }
}
