package nus.edu.u.user.domain.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nus.edu.u.common.enums.CommonStatusEnum;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Lu Shuwen
 * @date 2026-01-24
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserDTO {

    private Long id;

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

    @TableField(typeHandler = JacksonTypeHandler.class, value = "post_list")
    private List<Integer> postList;
}
