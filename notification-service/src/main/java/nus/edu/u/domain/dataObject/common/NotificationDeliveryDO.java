package nus.edu.u.domain.dataObject.common;

import jakarta.persistence.*;
import java.util.UUID;

import lombok.*;
import nus.edu.u.enums.common.NotificationChannel;
import nus.edu.u.enums.common.NotificationEventType;
import nus.edu.u.enums.common.NotificationStatus;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "notification_delivery",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_event_channel_recipient",
                        columnNames = {"event_id", "channel", "recipient_key"}),
        indexes = {
            @Index(name = "idx_recipient_created", columnList = "recipient_key, created_at DESC")
        })
public class NotificationDeliveryDO extends BaseNotificationEntity {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "event_id", nullable = false, length = 120)
    private String eventId;

    @Column(name = "recipient_key", nullable = false, length = 160)
    private String recipientKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private NotificationEventType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (status == null) status = NotificationStatus.CREATED;
    }
}
