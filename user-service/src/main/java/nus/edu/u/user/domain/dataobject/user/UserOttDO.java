package nus.edu.u.user.domain.dataobject.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName(value = "sys_user_ott")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserOttDO implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private Long userId;

    private String token;

    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    private LocalDateTime createdAt;
}
