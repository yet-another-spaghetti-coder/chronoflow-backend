package nus.edu.u.user.domain.vo.role;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author Lu Shuwen
 * @date 2025-09-29
 */
@Schema(description = "Role assign request VO")
@Data
public class RoleAssignReqVO {

    @Schema(description = "User ID", example = "1")
    private Long userId;

    @Schema(description = "Role ID list", example = "[1968589479571689474, 1968589479634604033]")
    private List<Long> roles;
}
