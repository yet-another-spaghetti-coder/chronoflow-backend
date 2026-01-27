package nus.edu.u.user.domain.vo.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Lu Shuwen
 * @date 2025-09-26
 */
@Schema(description = "Permission info response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionRespVO {

    @Schema(description = "Permission ID", example = "1")
    private Long id;

    @Schema(description = "Permission name", example = "Create member")
    private String name;

    @Schema(description = "Permission key", example = "system:organizer:member:create")
    private String key;

    @Schema(description = "Permission description", example = "Create member permission")
    private String description;
}
