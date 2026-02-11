package nus.edu.u.user.domain.vo.reg;


import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nus.edu.u.common.annotation.Mobile;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SsoRegOrganizerReqVO {


    @NotEmpty(message = "Jwt token is required")
    private String jwtToken;

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