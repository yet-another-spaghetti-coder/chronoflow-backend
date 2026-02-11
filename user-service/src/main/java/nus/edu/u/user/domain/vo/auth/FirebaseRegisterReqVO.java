package nus.edu.u.user.domain.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Firebase registration request VO.
 * The Firebase ID token is passed via Authorization header.
 */
@Data
@Schema(description = "Firebase registration request")
public class FirebaseRegisterReqVO {

    @NotBlank(message = "Display name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    @Schema(description = "User display name", example = "John Doe", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 200, message = "Organization name must be at most 200 characters")
    @Schema(description = "Organization name (optional)", example = "Acme Corp")
    private String organizationName;
}
