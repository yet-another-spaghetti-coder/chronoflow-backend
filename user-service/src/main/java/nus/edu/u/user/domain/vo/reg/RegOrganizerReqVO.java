package nus.edu.u.user.domain.vo.reg;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nus.edu.u.common.annotation.Mobile;
import nus.edu.u.common.annotation.StrongPassword;

/**
 * @author Lu Shuwen
 * @date 2025-09-10
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegOrganizerReqVO {

    @NotEmpty(message = "Organizer name is required")
    @Size(max = 100, message = "Organizer name must not exceed 100 characters")
    private String name;

    @NotEmpty(message = "Username is required")
    @Size(min = 6, max = 100, message = "Username must be between 6 and 100 characters")
    private String username;

    @NotEmpty(message = "Password is required")
    @StrongPassword
    private String userPassword;

    @NotEmpty(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String userEmail;

    @NotEmpty(message = "Mobile number is required")
    @Mobile
    private String mobile;

    @NotEmpty(message = "Organization name is required")
    @Size(max = 100, message = "Organization name must not exceed 100 characters")
    private String organizationName;

    @Size(max = 500, message = "Organization address must not exceed 500 characters")
    private String organizationAddress;

    @Size(min = 6, max = 20, message = "Organization code must be between 6 and 20 characters")
    private String organizationCode;
}
