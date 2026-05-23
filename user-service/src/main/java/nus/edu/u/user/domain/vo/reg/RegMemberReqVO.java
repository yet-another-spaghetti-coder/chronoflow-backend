package nus.edu.u.user.domain.vo.reg;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nus.edu.u.common.annotation.Mobile;
import nus.edu.u.common.annotation.StrongPassword;

/**
 * Register request VO
 *
 * @author Lu Shuwen
 * @date 2025-09-10
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegMemberReqVO {

    private Long userId;

    @NotEmpty(message = "Please enter your username")
    @Size(min = 6, max = 100, message = "Username must be between 6 and 100 characters")
    private String username;

    @NotEmpty(message = "Please enter your password")
    @StrongPassword
    private String password;

    @NotEmpty(message = "Please enter your phone number")
    @Mobile
    private String phone;
}
