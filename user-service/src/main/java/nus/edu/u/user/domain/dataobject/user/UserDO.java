package nus.edu.u.user.domain.dataobject.user;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import lombok.*;
import nus.edu.u.common.enums.CommonStatusEnum;
import nus.edu.u.framework.mybatis.base.TenantBaseDO;

/**
 * User data object for table sys_user
 *
 * @author Lu Shuwen
 * @date 2025-08-27
 */
@TableName(value = "sys_user", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDO extends TenantBaseDO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    @TableId private Long id;

    private String username;

    private String password;

    private String remark;

    private String email;

    private String phone;

    /**
     * User status
     *
     * <p>Enum {@link CommonStatusEnum}
     */
    private Integer status;

    private LocalDateTime loginTime;

    @Deprecated private Long deptId;

    @TableField(typeHandler = JacksonTypeHandler.class, value = "post_list")
    private List<Integer> postList;

    /** Firebase Authentication User UID */
    private String firebaseUid;

    /** TOTP secret key (encrypted) for two-factor authentication */
    private String totpSecret;

    /** Whether TOTP is enabled for this user */
    private Boolean totpEnabled;
}
