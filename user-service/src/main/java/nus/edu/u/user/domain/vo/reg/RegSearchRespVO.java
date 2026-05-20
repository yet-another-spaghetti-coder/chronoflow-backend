package nus.edu.u.user.domain.vo.reg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nus.edu.u.common.jackson.desensitize.Desensitize;
import nus.edu.u.common.jackson.desensitize.DesensitizeType;

/**
 * @author Lu Shuwen
 * @date 2025-09-10
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegSearchRespVO {

    private String organizationName;

    @Desensitize(type = DesensitizeType.EMAIL)
    private String email;
}
