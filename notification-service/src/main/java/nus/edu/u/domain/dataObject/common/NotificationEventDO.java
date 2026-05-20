package nus.edu.u.domain.dataObject.common;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import nus.edu.u.enums.common.NotificationEventType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(callSuper = false)
@Table(
        name = "notification_event",
        indexes = {
            // inbox queries: list notifications for a user (newest first)
            @Index(
                    name = "idx_notif_recipient_created",
                    columnList = "recipient_user_id,created_at"),
            // unread badge queries
            @Index(name = "idx_notif_recipient_read", columnList = "recipient_user_id,is_read"),
            // optional: analytics / admin queries
            @Index(name = "idx_notif_type_created", columnList = "type,created_at")
        })
public class NotificationEventDO extends BaseNotificationEntity {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "recipient_user_id", length = 64, nullable = false)
    private String recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 64, nullable = false)
    private NotificationEventType type;

    // for idempotency
    @Column(name = "event_id", length = 256)
    private String eventId;

    // who caused it (assigner / organizer / actor)
    @Column(name = "actor_id", length = 64)
    private String actorId;

    // generic target pointer (task, event, comment, etc.)
    @Column(name = "object_type", length = 32)
    private String objectType; // e.g. "TASK"

    @Column(name = "object_id", length = 64)
    private String objectId; // e.g. taskId

    @Column(name = "title", length = 64)
    private String title;

    // non-sensitive preview text (optional)
    @Column(name = "preview_text", length = 256)
    private String previewText;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    void init() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (read == null) read = false;
    }
}
