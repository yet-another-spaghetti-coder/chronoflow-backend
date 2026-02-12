package nus.edu.u.user.domain.vo.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Schema(description = "Audit log query request")
@Data
public class AuditLogQueryReqVO {

    @Schema(description = "User ID")
    private Long userId;

    @Schema(description = "Module name: user, event, task, attendee, file, security")
    private String module;

    @Schema(description = "Audit type: 1=Security 2=AdminAction 3=DataChange 4=APIAccess")
    private Integer type;

    @Schema(description = "Operation name")
    private String operation;

    @Schema(description = "Target entity type")
    private String targetType;

    @Schema(description = "Start time (inclusive)")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "End time (inclusive)")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Schema(description = "Page number (1-based)", example = "1")
    private Integer pageNo = 1;

    @Schema(description = "Page size", example = "20")
    private Integer pageSize = 20;
}
