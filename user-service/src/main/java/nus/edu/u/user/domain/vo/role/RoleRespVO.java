package nus.edu.u.user.domain.vo.role;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nus.edu.u.user.domain.vo.permission.PermissionRespVO;

@Schema(description = "Role info response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleRespVO {

    @Schema(description = "Role ID", example = "1")
    private Long id;

    @Schema(description = "Role name", example = "Manager")
    private String name;

    @Schema(description = "Role key", example = "MANAGER")
    private String key;

    @Schema(description = "If the role is the default role", example = "false")
    @Builder.Default private Boolean isDefault = false;

    @Schema(description = "Permissions info list")
    private List<PermissionRespVO> permissions;
}
