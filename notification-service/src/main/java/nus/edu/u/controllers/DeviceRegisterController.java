package nus.edu.u.controllers;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.domain.dto.common.DeviceRegisterDTO;
import nus.edu.u.domain.dto.common.RevokeDeviceDTO;
import nus.edu.u.enums.push.PushPlatform;
import nus.edu.u.services.push.DeviceRegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications/push/devices")
public class DeviceRegisterController {

    private final DeviceRegistryService deviceRegistryService;

    @PostMapping("/register")
    public ResponseEntity<Void> registerDevice(@RequestBody DeviceRegisterDTO dto) {
        StpUtil.checkLogin();
        String userId = String.valueOf(StpUtil.getLoginIdAsLong());

        log.info(
                "[PUSH] registerDevice userId={}, token?={}, deviceId={}, platform={}",
                userId,
                dto.getToken() != null
                        ? dto.getToken().substring(0, Math.min(12, dto.getToken().length()))
                        : null,
                dto.getDeviceId(),
                dto.getPlatform());

        if (dto.getPlatform() == null) dto.setPlatform(PushPlatform.WEB);
        deviceRegistryService.register(userId, dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/revoke-self")
    public ResponseEntity<Void> revokeSelf(@RequestBody RevokeDeviceDTO dto) {
        StpUtil.checkLogin();
        String userId = String.valueOf(StpUtil.getLoginIdAsLong());

        log.info("Data received userId = {}, deviceId={}", userId, dto.getDeviceId());
        deviceRegistryService.revokeByDeviceId(userId, dto.getDeviceId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/revoke-all")
    public ResponseEntity<Void> revokeAll() {
        StpUtil.checkLogin();

        String userId = String.valueOf(StpUtil.getLoginIdAsLong());
        deviceRegistryService.revokeAllForUser(userId);

        return ResponseEntity.ok().build();
    }
}
