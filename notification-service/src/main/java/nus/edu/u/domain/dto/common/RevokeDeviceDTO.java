package nus.edu.u.domain.dto.common;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RevokeDeviceDTO {
    @NotBlank private String deviceId;
}
