package nus.edu.u.user.domain.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Lu Shuwen
 * @date 2025-09-01
 */
@Schema(description = "User info VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserVO {

    @Schema(description = "User ID", example = "1968589479517163522")
    private Long id;

    @Schema(description = "Username", example = "lushuwen")
    private String name;

    @Schema(description = "Email", example = "123@u.nus.edu")
    private String email;

    @Schema(description = "Role name", example = "Manager")
    private String role;
}
