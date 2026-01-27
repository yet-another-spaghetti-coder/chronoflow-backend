package nus.edu.u.user.domain.vo.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * @author Lu Shuwen
 * @date 2025-09-25
 */
@Schema(description = "Role info request VO")
@Data
public class RoleReqVO {

    @Schema(description = "Role name", requiredMode = Schema.RequiredMode.REQUIRED, example = "Manager")
    @Size(min = 1, max = 100, message = "Role name should between 1 and 100")
    private String name;

    @Schema(description = "Role key", requiredMode = Schema.RequiredMode.REQUIRED, example = "MANAGER")
    @Size(min = 1, max = 50, message = "Role key should between 1 and 50")
    private String key;

    @Schema(description = "Permissions id list", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1971465366969307139, 1971465366969307140]")
    private List<Long> permissions;
}
