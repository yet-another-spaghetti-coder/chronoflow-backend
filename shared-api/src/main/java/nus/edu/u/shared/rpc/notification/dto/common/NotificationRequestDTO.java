package nus.edu.u.shared.rpc.notification.dto.common;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nus.edu.u.shared.rpc.notification.enums.NotificationChannel;
import nus.edu.u.shared.rpc.notification.enums.NotificationEventType;
import nus.edu.u.shared.rpc.notification.enums.NotificationObjectType;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequestDTO {

    private NotificationChannel channel;

    // lagacy fields
    private String to;
    private String recipientKey;
    private String templateId;
    private Map<String, Object> variables;
    private Locale locale;
    private List<AttachmentDTO> attachments;

    private String eventId;

    // --- new "notification_event" aligned fields ---
    private String recipientUserId; // maps to notification.recipient_user_id
    private String actorId; // maps to notification.actor_id
    private NotificationObjectType objectType; // maps to notification.object_type (e.g. TASK)
    private String objectId; // maps to notification.object_id
    private String previewText; // maps to notification.preview_text (safe)
    private String title;

    private NotificationEventType type;

    // maybe remove later
    private String userId;
}
