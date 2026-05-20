package nus.edu.u.services.push;

import java.util.List;
import nus.edu.u.domain.dataObject.common.NotificationDeviceDO;
import nus.edu.u.domain.dto.common.DeviceRegisterDTO;
import nus.edu.u.domain.dto.common.NotificationDeviceViewDTO;

public interface DeviceRegistryService {
    void register(String userId, DeviceRegisterDTO dto);

    void revokeByToken(String token);

    void revokeByTokenForUser(String userId, String token);

    /** Old: returns entities (no caching) */
    List<NotificationDeviceDO> activeDevices(String userId);

    /** New: returns cache-friendly DTOs */
    List<NotificationDeviceViewDTO> activeDeviceViews(String userId);

    void revokeAllForUser(String userId);

    void revokeByDeviceId(String userId, String deviceId);
}
