package nus.edu.u.user.domain.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nus.edu.u.common.annotation.StrongPassword;

/** Reset password request VO */
@Schema(description = "Reset password request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResetPasswordReqVO {

    @Schema(
            description = "Single-use password reset token delivered by email",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "Token is required")
    @Size(min = 16, max = 200, message = "Token format is invalid")
    private String token;

    @Schema(
            description =
                    "New password — must meet the ChronoFlow password policy (12–128 chars,"
                            + " upper/lower/digit/symbol, not commonly breached)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "New password is required")
    @StrongPassword
    private String newPassword;
}
