package nus.edu.u.services.push;

import com.google.firebase.messaging.FirebaseMessagingException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.configuration.push.PushLimitPropertiesConfig;
import nus.edu.u.domain.dataObject.common.NotificationDeliveryDO;
import nus.edu.u.domain.dataObject.push.PushMessageDO;
import nus.edu.u.domain.dto.push.PushRequestDTO;
import nus.edu.u.enums.common.NotificationChannel;
import nus.edu.u.enums.common.NotificationStatus;
import nus.edu.u.enums.push.PushStatus;
import nus.edu.u.exception.RateLimitExceededException;
import nus.edu.u.provider.push.PushClient;
import nus.edu.u.repositories.common.NotificationDeliveryRepository;
import nus.edu.u.repositories.push.PushMessageRepository;
import nus.edu.u.services.rateLimiter.RateLimiter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushServiceImpl implements PushService {

    private final NotificationDeliveryRepository deliveryRepo;
    private final PushMessageRepository pushRepo;
    private final RateLimiter rateLimiter;
    private final PushLimitPropertiesConfig props;
    private final PushClient pushClient;
    private final DeviceRegistryService deviceRegistry;

    // -----------------
    // 1) Fan-out to all active devices of a user
    // -----------------
    @Override
    public Map<String, String> sendToUser(String userId, PushRequestDTO base) {
        var results = new LinkedHashMap<String, String>();
        var devices = deviceRegistry.activeDevices(userId);

        if (devices.isEmpty()) {
            log.info("Push skipped: no active devices for userId={}", userId);
            return results; // empty => caller can infer NO_DEVICES
        }

        for (var d : devices) {
            var dto =
                    PushRequestDTO.builder()
                            .eventId(base.getEventId())
                            .recipientKey("push:device:" + d.getDeviceId())
                            .token(d.getToken())
                            .title(base.getTitle())
                            .body(base.getBody())
                            .data(base.getData())
                            .type(base.getType())
                            .build();

            String status = this.send(dto);
            results.put(d.getId(), status);
        }
        return results;
    }

    // ------------------------
    // 2) Single-device send with UNREGISTERED handling
    // ------------------------
    @Override
    @Transactional
    public String send(PushRequestDTO dto) {
        // --- Guards ---
        if (!rateLimiter.allow(props.getRateKey(), props.getRateLimit(), props.getRateWindow())) {
            throw new RateLimitExceededException("Rate limit exceeded for push");
        }
        if (dto.getEventId() == null || dto.getEventId().isBlank())
            throw new IllegalArgumentException("eventId is required");
        if (dto.getRecipientKey() == null || dto.getRecipientKey().isBlank())
            throw new IllegalArgumentException("recipientKey is required");
        if (dto.getToken() == null || dto.getToken().isBlank())
            throw new IllegalArgumentException("token is required");
        if (dto.getType() == null)
            throw new IllegalArgumentException("type (NotificationEventType) is required");

        NotificationDeliveryDO delivery = null;
        PushMessageDO pushRow = null;

        try {
            // 1) Insert delivery and FLUSH so unique constraint triggers before external call
            delivery =
                    deliveryRepo.saveAndFlush(
                            NotificationDeliveryDO.builder()
                                    .eventId(dto.getEventId())
                                    .recipientKey(dto.getRecipientKey())
                                    .channel(NotificationChannel.PUSH)
                                    .type(dto.getType())
                                    .status(NotificationStatus.CREATED)
                                    .build());

            // 2) Channel row (PENDING)
            pushRow =
                    pushRepo.save(
                            PushMessageDO.builder()
                                    .delivery(delivery)
                                    .token(dto.getToken())
                                    .status(PushStatus.PENDING)
                                    .build());

            // 3) Send via provider
            Map<String, Object> data =
                    dto.getData() == null ? Collections.emptyMap() : dto.getData();
            String providerMsgId =
                    pushClient.send(dto.getToken(), dto.getTitle(), dto.getBody(), data);

            // 4) Success state
            pushRow.setFcmId(providerMsgId);
            pushRepo.save(pushRow.markSent(providerMsgId));

            delivery.setStatus(NotificationStatus.DELIVERED);
            deliveryRepo.save(delivery);

            log.info(
                    "Push DELIVERED: eventId={}, recipientKey={}, token={}",
                    dto.getEventId(),
                    dto.getRecipientKey(),
                    dto.getToken());
            return "ACCEPTED";

        } catch (DataIntegrityViolationException dup) {
            // idempotent duplicate (eventId + channel + recipientKey)
            log.info(
                    "Duplicate push suppressed (idempotent): eventId={}, recipientKey={}",
                    dto.getEventId(),
                    dto.getRecipientKey());
            return "ALREADY_ACCEPTED";

        } catch (FirebaseMessagingException fme) {
            // --- Provider error handling (UNREGISTERED etc.) ---
            String code =
                    (fme.getMessagingErrorCode() != null)
                            ? fme.getMessagingErrorCode().name()
                            : null;
            log.warn(
                    "FCM error: code={}, message={}, token={}",
                    code,
                    fme.getMessage(),
                    dto.getToken());

            // If token is invalid/expired, immediately revoke it so we won't reuse it
            if ("UNREGISTERED".equals(code)) {
                try {
                    deviceRegistry.revokeByToken(dto.getToken());
                    log.info("Token revoked due to UNREGISTERED: {}", dto.getToken());
                } catch (Exception ignore) {
                    // keep original failure
                }
            }

            // best-effort state marking
            try {
                if (pushRow != null) pushRepo.save(pushRow.markFailed(fme.getMessage()));
            } catch (Exception ignore) {
            }
            try {
                if (delivery != null) {
                    delivery.setStatus(NotificationStatus.FAILED);
                    deliveryRepo.save(delivery);
                }
            } catch (Exception ignore) {
            }
            return "FAILED";

        } catch (Exception ex) {
            // generic failure
            log.warn(
                    "Push FAILED: eventId={}, recipientKey={}, token={}, err={}",
                    dto.getEventId(),
                    dto.getRecipientKey(),
                    dto.getToken(),
                    ex.getMessage(),
                    ex);
            try {
                if (pushRow != null) pushRepo.save(pushRow.markFailed(ex.getMessage()));
            } catch (Exception ignore) {
            }
            try {
                if (delivery != null) {
                    delivery.setStatus(NotificationStatus.FAILED);
                    deliveryRepo.save(delivery);
                }
            } catch (Exception ignore) {
            }
            return "FAILED";
        }
    }
}
