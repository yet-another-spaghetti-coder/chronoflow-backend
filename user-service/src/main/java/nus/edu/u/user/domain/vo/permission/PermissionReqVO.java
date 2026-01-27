package nus.edu.u.user.domain.vo.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author Lu Shuwen
 * @date 2025-09-29
 */
@Schema(description = "Permission info request VO")
@Data
public class PermissionReqVO {

    @Schema(description = "Permission name", requiredMode = Schema.RequiredMode.REQUIRED, example = "Create member")
    private String name;

    @Schema(description = "Permission key", requiredMode = Schema.RequiredMode.REQUIRED, example = "system:organizer:member:create")
    private String key;

    @Schema(description = "Permission description", requiredMode = Schema.RequiredMode.REQUIRED, example = "Create member permission")
    private String description;
}
