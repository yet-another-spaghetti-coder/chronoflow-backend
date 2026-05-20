package nus.edu.u.services.push;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import nus.edu.u.domain.dataObject.common.NotificationDeviceDO;
import nus.edu.u.domain.dto.common.DeviceRegisterDTO;
import nus.edu.u.domain.dto.common.NotificationDeviceViewDTO;
import nus.edu.u.enums.common.DeviceStatus;
import nus.edu.u.enums.push.PushPlatform;
import nus.edu.u.repositories.common.NotificationDeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceRegistryServiceImpl implements DeviceRegistryService {

    private static final String CACHE_NAME = "devices:activeByUser";

    private final NotificationDeviceRepository repo;

    @Override
    @Transactional
    public void register(String userId, DeviceRegisterDTO dto) {
        if (userId == null || userId.isBlank())
            throw new IllegalArgumentException("userId is required");
        if (dto == null) throw new IllegalArgumentException("body is required");

        String deviceId = dto.getDeviceId() == null ? null : dto.getDeviceId().trim();
        String token = dto.getToken() == null ? null : dto.getToken().trim();

        if (deviceId == null || deviceId.isBlank())
            throw new IllegalArgumentException("deviceId is required");
        if (token == null || token.isBlank())
            throw new IllegalArgumentException("token is required");

        PushPlatform platform = Optional.ofNullable(dto.getPlatform()).orElse(PushPlatform.WEB);

        // 1) Upsert by (userId, deviceId)
        NotificationDeviceDO device = repo.findByUserIdAndDeviceId(userId, deviceId).orElse(null);

        if (device == null) {
            // optional: if token is already stored elsewhere, revoke it (enforce uniqueness)
            repo.findByToken(token)
                    .forEach(
                            other -> {
                                // If the token is linked to a different device record, revoke that
                                // record
                                // (or you can delete it, but revoke is safer/auditable)
                                if (!other.getUserId().equals(userId)
                                        || !other.getDeviceId().equals(deviceId)) {
                                    other.setStatus(DeviceStatus.REVOKED);
                                    repo.save(other);
                                }
                            });

            repo.save(
                    NotificationDeviceDO.builder()
                            .userId(userId)
                            .deviceId(deviceId)
                            .platform(platform)
                            .token(token)
                            .status(DeviceStatus.ACTIVE)
                            .build());
            return;
        }

        // 2) Existing device row: update mutable fields
        boolean changed = false;

        if (!token.equals(device.getToken())) {
            // optional: enforce token uniqueness by revoking the other row that holds this token
            repo.findByToken(token)
                    .forEach(
                            other -> {
                                if (!other.getId().equals(device.getId())) {
                                    other.setStatus(DeviceStatus.REVOKED);
                                    repo.save(other);
                                }
                            });

            device.setToken(token);
            changed = true;
        }

        if (device.getPlatform() != platform) {
            device.setPlatform(platform);
            changed = true;
        }

        if (device.getStatus() != DeviceStatus.ACTIVE) {
            device.setStatus(DeviceStatus.ACTIVE);
            changed = true;
        }

        if (changed) repo.save(device);
    }

    @Override
    @Transactional
    public void revokeByToken(String token) {
        if (token == null || token.isBlank()) return;
        repo.findByToken(token.trim())
                .forEach(
                        d -> {
                            d.setStatus(DeviceStatus.REVOKED);
                            repo.save(d);
                        });
    }

    @Override
    @Transactional
    public void revokeByDeviceId(String userId, String deviceId) {
        if (userId == null || userId.isBlank())
            throw new IllegalArgumentException("userId is required");
        if (deviceId == null || deviceId.isBlank())
            throw new IllegalArgumentException("deviceId is required");

        repo.findByUserIdAndDeviceId(userId, deviceId.trim())
                .ifPresent(
                        d -> {
                            d.setStatus(DeviceStatus.REVOKED);
                            repo.save(d);
                        });
    }

    @Override
    @Transactional
    public void revokeByTokenForUser(String userId, String token) {
        if (userId == null || userId.isBlank())
            throw new IllegalArgumentException("userId is required");
        if (token == null || token.isBlank()) return;

        repo.findByUserIdAndToken(userId, token.trim())
                .ifPresent(
                        d -> {
                            d.setStatus(DeviceStatus.REVOKED);
                            repo.save(d);
                        });

        // Intentionally do nothing if not found:
        // - idempotent
        // - avoids leaking whether a token exists / belongs to someone else
    }

    /** Keep for internal use (no caching) */
    @Override
    @Transactional(readOnly = true)
    public List<NotificationDeviceDO> activeDevices(String userId) {
        if (userId == null || userId.isBlank())
            throw new IllegalArgumentException("userId is required");
        return repo.findByUserIdAndStatus(userId, DeviceStatus.ACTIVE);
    }

    /** Cache the thin DTOs */
    @Override
    @Transactional(readOnly = true)
    public List<NotificationDeviceViewDTO> activeDeviceViews(String userId) {
        if (userId == null || userId.isBlank())
            throw new IllegalArgumentException("userId is required");
        return repo.findByUserIdAndStatus(userId, DeviceStatus.ACTIVE).stream()
                .map(
                        d ->
                                NotificationDeviceViewDTO.builder()
                                        .id(d.getId())
                                        .token(d.getToken())
                                        .platform(d.getPlatform())
                                        .status(d.getStatus())
                                        .build())
                .toList();
    }

    @Override
    @Transactional
    public void revokeAllForUser(String userId) {
        var active = repo.findByUserIdAndStatus(userId, DeviceStatus.ACTIVE);
        for (var d : active) {
            d.setStatus(DeviceStatus.REVOKED);
        }
        repo.saveAll(active);
    }
}
