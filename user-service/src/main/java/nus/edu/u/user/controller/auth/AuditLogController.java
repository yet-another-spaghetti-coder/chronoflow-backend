package nus.edu.u.user.controller.auth;

import static nus.edu.u.common.core.domain.CommonResult.success;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import nus.edu.u.common.core.domain.CommonResult;
import nus.edu.u.common.core.domain.PageResult;
import nus.edu.u.user.domain.vo.audit.AuditLogQueryReqVO;
import nus.edu.u.user.domain.vo.audit.AuditLogRespVO;
import nus.edu.u.user.service.audit.AuditLogService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Audit Log Controller")
@RestController
@RequestMapping("/users/audit-logs")
@Validated
public class AuditLogController {

    @Resource
    private AuditLogService auditLogService;

    @GetMapping
    @Operation(summary = "Get paginated audit logs with filters")
    public CommonResult<PageResult<AuditLogRespVO>> getAuditLogs(AuditLogQueryReqVO reqVO) {
        return success(auditLogService.getAuditLogPage(reqVO));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get audit log by ID")
    public CommonResult<AuditLogRespVO> getAuditLog(@PathVariable Long id) {
        return success(auditLogService.getAuditLog(id));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get audit logs for a specific user")
    public CommonResult<PageResult<AuditLogRespVO>> getAuditLogsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return success(auditLogService.getAuditLogsByUserId(userId, pageNo, pageSize));
    }
}
