package nus.edu.u.user.domain.vo.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nus.edu.u.user.domain.vo.role.RoleRespVO;

/** User login response VO */
@Schema(description = "Login response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRespVO {

    @Schema(description = "User info")
    private UserVO user;

    @Schema(description = "User roles list")
    private List<RoleRespVO> roles;

    @JsonIgnore private String refreshToken;
}
