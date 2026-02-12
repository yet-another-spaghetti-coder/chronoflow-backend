package nus.edu.u.user.domain.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request VO for TOTP verification.
 */
@Schema(description = "TOTP Verification Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TotpVerifyReqVO {

    @Schema(description = "6-digit TOTP code from authenticator app")
    @NotBlank(message = "TOTP code is required")
    @Pattern(regexp = "^\\d{6}$", message = "TOTP code must be 6 digits")
    private String code;

    @Schema(description = "MFA token from login response (required for login verification)")
    private String mfaToken;

    @Schema(description = "Secret key (required for setup verification)")
    private String secret;
}
