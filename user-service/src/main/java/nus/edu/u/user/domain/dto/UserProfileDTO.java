package nus.edu.u.user.domain.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nus.edu.u.common.jackson.desensitize.Desensitize;
import nus.edu.u.common.jackson.desensitize.DesensitizeType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private Long id;
    private String name;
    private String email;

    @Desensitize(type = DesensitizeType.PHONE)
    private String phone;

    private List<Long> roles;
    private boolean isRegistered;
}
