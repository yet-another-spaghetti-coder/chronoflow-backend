package nus.edu.u.domain.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nus.edu.u.enums.common.DeviceStatus;
import nus.edu.u.enums.push.PushPlatform;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegisterDTO {

    /** FCM registration token (required) */
    private String token;

    private String deviceId;

    /** Platform type (optional — defaults to WEB) */
    @Builder.Default private PushPlatform platform = PushPlatform.WEB;

    /** Device status (optional, usually ACTIVE on registration) */
    @Builder.Default private DeviceStatus status = DeviceStatus.ACTIVE;

    /** Browser-generated public key for provider-blind web push encryption. */
    private String pushPublicKey;

    /** Public key/envelope algorithm, for example ECDH-P256+A256GCM. */
    private String pushKeyAlg;

    /** Client-side key version used for rotation/debug evidence. */
    private String pushKeyVersion;
}
