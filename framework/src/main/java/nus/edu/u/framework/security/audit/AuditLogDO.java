package nus.edu.u.framework.security.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import nus.edu.u.framework.mybatis.base.TenantBaseDO;

/**
 * Persistent audit log entry stored in the {@code audit_log} table.
 */
@TableName("audit_log")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDO extends TenantBaseDO implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Distributed trace ID for request correlation. */
    private String traceId;

    /** ID of the user who triggered the action. */
    private Long userId;

    /** Client IP address. */
    private String userIp;

    /** Client User-Agent header. */
    private String userAgent;

    /** Logical module: user, event, task, attendee, file, security. */
    private String module;

    /** Human-readable operation name, e.g. "Create Role", "LOGIN_SUCCESS". */
    private String operation;

    /** Audit type: 1=Security 2=AdminAction 3=DataChange 4=APIAccess. */
    private Integer type;

    /** HTTP method (GET, POST, PUT, DELETE, PATCH). */
    private String method;

    /** Request URL. */
    private String requestUrl;

    /** Sanitized request body (passwords stripped). */
    private String requestBody;

    /** Logical entity type: Role, Permission, Event, Task, etc. */
    private String targetType;

    /** Target entity ID. */
    private String targetId;

    /** JSON snapshot of entity state before modification. */
    private String beforeData;

    /** JSON snapshot of entity state after modification. */
    private String afterData;

    /** HTTP response code or business result code. */
    private Integer resultCode;

    /** Result message (error message on failure). */
    private String resultMsg;

    /** Operation duration in milliseconds. */
    private Integer duration;

    /** Arbitrary extra data as JSON. */
    private String extra;
}
