package nus.edu.u.user.domain.vo.user;

import java.util.List;
import lombok.Data;
import nus.edu.u.common.jackson.desensitize.Desensitize;
import nus.edu.u.common.jackson.desensitize.DesensitizeType;

@Data
public class UserProfileRespVO {
    private Long id;
    private String name;
    private String email;

    @Desensitize(type = DesensitizeType.PHONE)
    private String phone;

    private List<Long> roles;
    private boolean isRegistered;
}
