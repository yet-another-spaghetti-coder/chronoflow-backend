package nus.edu.u.user.domain.vo.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
public class CreateProfileReqVO {
    @NotBlank(message = "Username cannot be blank")
    @Size(min = 4, max = 32, message = "Username length must be 4~32")
    private String username;

    @NotBlank(message = "Password cannot be blank")
    @StrongPassword
    private String password;

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email format invalid")
    private String email;

    @NotBlank(message = "Phone cannot be blank")
    private String phone;

    private String remark;
}
