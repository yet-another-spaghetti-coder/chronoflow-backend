package nus.edu.u.repositories.common;

import java.util.List;
import java.util.Optional;
import nus.edu.u.domain.dataObject.common.NotificationDeviceDO;
import nus.edu.u.enums.common.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationDeviceRepository extends JpaRepository<NotificationDeviceDO, String> {
    List<NotificationDeviceDO> findByToken(String token);

    List<NotificationDeviceDO> findByUserIdAndStatus(String userId, DeviceStatus status);

    Optional<NotificationDeviceDO> findByUserIdAndToken(String userId, String token);

    Optional<NotificationDeviceDO> findByUserIdAndDeviceId(String userId, String deviceId);
}
