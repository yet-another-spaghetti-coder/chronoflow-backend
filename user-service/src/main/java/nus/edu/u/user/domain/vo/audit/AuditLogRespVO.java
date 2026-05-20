package nus.edu.u.user.domain.vo.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Audit log response")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogRespVO {

    @Schema(description = "Audit log ID")
    private Long id;

    @Schema(description = "Trace ID")
    private String traceId;

    @Schema(description = "User ID")
    private Long userId;

    @Schema(description = "Client IP")
    private String userIp;

    @Schema(description = "User Agent")
    private String userAgent;

    @Schema(description = "Module")
    private String module;

    @Schema(description = "Operation")
    private String operation;

    @Schema(description = "Audit type: 1=Security 2=AdminAction 3=DataChange 4=APIAccess")
    private Integer type;

    @Schema(description = "HTTP method")
    private String method;

    @Schema(description = "Request URL")
    private String requestUrl;

    @Schema(description = "Request body")
    private String requestBody;

    @Schema(description = "Target entity type")
    private String targetType;

    @Schema(description = "Target entity ID")
    private String targetId;

    @Schema(description = "Before data (JSON)")
    private String beforeData;

    @Schema(description = "After data (JSON)")
    private String afterData;

    @Schema(description = "Result code")
    private Integer resultCode;

    @Schema(description = "Result message")
    private String resultMsg;

    @Schema(description = "Duration in milliseconds")
    private Integer duration;

    @Schema(description = "Extra data (JSON)")
    private String extra;

    @Schema(description = "Created time")
    private LocalDateTime createTime;
}
