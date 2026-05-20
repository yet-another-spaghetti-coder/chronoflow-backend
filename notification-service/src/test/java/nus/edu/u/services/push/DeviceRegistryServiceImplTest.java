package nus.edu.u.services.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import nus.edu.u.domain.dataObject.common.NotificationDeviceDO;
import nus.edu.u.domain.dto.common.DeviceRegisterDTO;
import nus.edu.u.domain.dto.common.NotificationDeviceViewDTO;
import nus.edu.u.enums.common.DeviceStatus;
import nus.edu.u.enums.push.PushPlatform;
import nus.edu.u.repositories.common.NotificationDeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceRegistryServiceImplTest {

    @Mock private NotificationDeviceRepository repository;

    @InjectMocks private DeviceRegistryServiceImpl service;

    @Test
    void register_requiresValidInputs() {
        DeviceRegisterDTO dto = DeviceRegisterDTO.builder().token("token").build();

        assertThatThrownBy(() -> service.register(null, dto))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.register("user", null))
                .isInstanceOf(IllegalArgumentException.class);

        DeviceRegisterDTO emptyToken = DeviceRegisterDTO.builder().token(" ").build();
        assertThatThrownBy(() -> service.register("user", emptyToken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revokeByToken_marksDeviceAsRevoked() {
        NotificationDeviceDO device =
                NotificationDeviceDO.builder()
                        .userId("user")
                        .token("token-1")
                        .status(DeviceStatus.ACTIVE)
                        .build();
        when(repository.findByToken("token-1")).thenReturn(List.of(device));

        service.revokeByToken("token-1");

        assertThat(device.getStatus()).isEqualTo(DeviceStatus.REVOKED);
        verify(repository).save(device);
    }

    @Test
    void revokeByToken_ignoresBlankTokens() {
        service.revokeByToken(" ");
        verify(repository, never()).findByToken(any());
    }

    @Test
    void activeDevices_requiresUserId() {
        assertThatThrownBy(() -> service.activeDevices(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activeDeviceViews_transformsEntities() {
        NotificationDeviceDO device =
                NotificationDeviceDO.builder()
                        .id("id-1")
                        .userId("user")
                        .token("tok")
                        .platform(PushPlatform.ANDROID)
                        .status(DeviceStatus.ACTIVE)
                        .build();
        when(repository.findByUserIdAndStatus("user", DeviceStatus.ACTIVE))
                .thenReturn(List.of(device));

        List<NotificationDeviceViewDTO> results = service.activeDeviceViews("user");

        assertThat(results)
                .containsExactly(
                        NotificationDeviceViewDTO.builder()
                                .id("id-1")
                                .token("tok")
                                .platform(PushPlatform.ANDROID)
                                .status(DeviceStatus.ACTIVE)
                                .build());
    }

    @Test
    void revokeAllForUser_setsStatusAndSavesAll() {
        NotificationDeviceDO active1 =
                NotificationDeviceDO.builder().userId("user").status(DeviceStatus.ACTIVE).build();
        NotificationDeviceDO active2 =
                NotificationDeviceDO.builder().userId("user").status(DeviceStatus.ACTIVE).build();
        List<NotificationDeviceDO> activeDevices = new ArrayList<>(List.of(active1, active2));
        when(repository.findByUserIdAndStatus("user", DeviceStatus.ACTIVE))
                .thenReturn(activeDevices);

        service.revokeAllForUser("user");

        assertThat(activeDevices).allMatch(device -> device.getStatus() == DeviceStatus.REVOKED);
        verify(repository).saveAll(activeDevices);
    }
}
