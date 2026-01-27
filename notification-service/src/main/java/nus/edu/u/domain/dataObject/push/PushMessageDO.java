package nus.edu.u.domain.dataObject.push;

import jakarta.persistence.*;
import lombok.*;
import nus.edu.u.domain.dataObject.common.BaseNotificationEntity;
import nus.edu.u.domain.dataObject.common.NotificationDeliveryDO;
import nus.edu.u.enums.push.PushStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(callSuper=false)
@Table(name = "push_message")
public class PushMessageDO extends BaseNotificationEntity {

    /** Primary key doubles as FK to notification_delivery.id */
    @Id
    @Column(name = "delivery_id", length = 36, nullable = false)
    private String deliveryId;

    /** Back-reference to master delivery row (no @MapsId to avoid duplicate INSERTs) */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "delivery_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_push_delivery"))
    private NotificationDeliveryDO delivery;

    /** Device token used for FCM/APNs */
    @Column(length = 512)
    private String token;

    /** FCM message ID returned by Firebase */
    @Column(name = "fcm_id", length = 200)
    private String fcmId;

    /** PENDING | SENT | FAILED */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private PushStatus status = PushStatus.PENDING;

    /** Error message (nullable) */
    @Lob private String errorMessage;

    @PrePersist
    void syncPkFromParent() {
        if (this.deliveryId == null && this.delivery != null) {
            this.deliveryId = this.delivery.getId();
        }
    }

    // Helpers
    public PushMessageDO markSent(String fcmId) {
        this.status = PushStatus.SENT;
        this.fcmId = fcmId;
        this.errorMessage = null;
        return this;
    }

    public PushMessageDO markFailed(String error) {
        this.status = PushStatus.FAILED;
        this.errorMessage = error;
        this.fcmId = null;
        return this;
    }
}
