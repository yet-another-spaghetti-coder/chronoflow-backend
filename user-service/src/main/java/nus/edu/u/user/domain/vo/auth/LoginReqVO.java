package nus.edu.u.user.domain.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** User login request VO */
@Schema(description = "User login request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginReqVO {

    @Schema(description = "Username", requiredMode = Schema.RequiredMode.REQUIRED, example = "lushuwen")
    @NotEmpty(message = "Username is required")
    @Size(min = 6, max = 100, message = "Username must be between 6 and 100 characters")
    private String username;

    @Schema(description = "Password", requiredMode = Schema.RequiredMode.REQUIRED, example = "lushuwen")
    @NotEmpty(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;

    @Schema(description = "Need remember account", example = "true")
    @Builder.Default private boolean remember = true;

    private String refreshToken;
}
