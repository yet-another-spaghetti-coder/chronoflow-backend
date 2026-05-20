package nus.edu.u.shared.rpc.user;

import java.io.Serial;
import java.io.Serializable;
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
public class UserProfileDTO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String email;

    @Desensitize(type = DesensitizeType.PHONE)
    private String phone;

    private List<Long> roles;
    private boolean isRegistered;
}
