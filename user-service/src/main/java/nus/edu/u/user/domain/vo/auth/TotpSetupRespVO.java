package nus.edu.u.user.domain.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response VO for TOTP setup - contains QR code and secret for authenticator app.
 */
@Schema(description = "TOTP Setup Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TotpSetupRespVO {

    @Schema(description = "Base64 encoded QR code image as data URI")
    private String qrCodeDataUri;

    @Schema(description = "Secret key for manual entry in authenticator app")
    private String secret;

    @Schema(description = "TOTP URI for manual entry")
    private String totpUri;
}
