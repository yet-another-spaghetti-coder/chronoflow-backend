package nus.edu.u.user.domain.vo.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nus.edu.u.common.annotation.StrongPassword;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileReqVO {
    @Size(min = 4, max = 32, message = "Username length must be 4~32")
    private String username;

    @StrongPassword private String password;

    @Email(message = "Email format invalid")
    private String email;

    private String phone;

    private String remark;
}
