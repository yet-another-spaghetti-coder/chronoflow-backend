package nus.edu.u.domain.dataObject.common;

import jakarta.persistence.*;
import lombok.*;
import nus.edu.u.enums.common.DeviceStatus;
import nus.edu.u.enums.push.PushPlatform;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(callSuper=false)
@Table(
        name = "notification_device",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_token", columnNames = "token"),
        indexes = @Index(name = "idx_device_user_status", columnList = "user_id,status"))
public class NotificationDeviceDO extends BaseNotificationEntity {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", length = 16, nullable = false)
    @Builder.Default
    private PushPlatform platform = PushPlatform.WEB;

    @Column(name = "token", length = 1024, nullable = false)
    private String token; // FCM registration token

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    @Builder.Default
    private DeviceStatus status = DeviceStatus.ACTIVE; // ACTIVE | INVALID | REVOKED

    @PrePersist
    void init() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
    }
}
