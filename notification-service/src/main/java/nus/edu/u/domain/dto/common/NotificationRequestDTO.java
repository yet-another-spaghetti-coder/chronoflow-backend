package nus.edu.u.domain.dto.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nus.edu.u.enums.common.NotificationChannel;
import nus.edu.u.enums.common.NotificationEventType;
import nus.edu.u.shared.rpc.notification.enums.NotificationObjectType;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequestDTO {

    // ---- routing / envelope ----
    @NotNull private NotificationChannel channel;

    /**
     * Idempotency key for this notification request. (You already use it across channels; keep it.)
     */
    @NotBlank private String eventId;

    @NotNull private NotificationEventType type;

    // ---- NEW: notification_event aligned fields (push/ws future) ----
    /**
     * Who receives the notification (inbox owner). For PUSH you will use this to fan-out to
     * devices.
     */
    @NotBlank private String recipientUserId;

    /** Who caused it (assigner / actor). Optional. */
    private String actorId;

    /** Generic target type (TASK/EVENT/COMMENT...). Optional but recommended. */
    @NotNull private NotificationObjectType objectType;

    /** Generic target id (taskId, eventId, commentId...). Optional but recommended. */
    @NotBlank private String objectId;

    @NotBlank private String title;

    /** Non-sensitive preview used for push body/inbox list. Optional. */
    private String previewText;

    // ---- LEGACY fields (keep only while email/ws still using them) ----
    private String to; // e.g. email address
    private String
            recipientKey; // e.g. "email:foo@bar.com" (push no longer needs it from publisher)
    private String templateId; // email template, ws legacy
    private Map<String, Object> variables;
    private Locale locale;
    private List<AttachmentDTO> attachments;

    /**
     * TEMP only: to avoid breaking existing code paths that still call getUserId() (e.g., your
     * current push sender expects userId). Plan to remove.
     */
    private String userId;

    // ---- Immutable-style helper methods ----

    public NotificationRequestDTO withLocale(Locale locale) {
        return this.toBuilder().locale(locale).build();
    }

    public NotificationRequestDTO withVariables(Map<String, Object> vars) {
        return this.toBuilder().variables(vars).build();
    }

    public NotificationRequestDTO withTemplateId(String templateId) {
        return this.toBuilder().templateId(templateId).build();
    }

    public NotificationRequestDTO withRecipientUserId(String recipientUserId) {
        return this.toBuilder().recipientUserId(recipientUserId).build();
    }

    public NotificationRequestDTO withPreviewText(String previewText) {
        return this.toBuilder().previewText(previewText).build();
    }
}
