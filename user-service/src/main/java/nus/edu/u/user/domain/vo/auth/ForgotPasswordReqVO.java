package nus.edu.u.user.domain.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Forgot password request VO */
@Schema(description = "Forgot password request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForgotPasswordReqVO {

    @Schema(
            description = "Email address of the account requesting password reset",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "user@example.com")
    @NotEmpty(message = "Email is required")
    @Email(message = "Email is invalid")
    @Size(max = 254, message = "Email is too long")
    private String email;
}
