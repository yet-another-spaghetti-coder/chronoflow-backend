package nus.edu.u.user.service.audit;

import nus.edu.u.common.core.domain.PageResult;
import nus.edu.u.user.domain.vo.audit.AuditLogQueryReqVO;
import nus.edu.u.user.domain.vo.audit.AuditLogRespVO;

public interface AuditLogService {

    /**
     * Query audit logs with pagination and filtering.
     */
    PageResult<AuditLogRespVO> getAuditLogPage(AuditLogQueryReqVO reqVO);

    /**
     * Get a single audit log by ID.
     */
    AuditLogRespVO getAuditLog(Long id);

    /**
     * Get audit logs for a specific user with pagination.
     */
    PageResult<AuditLogRespVO> getAuditLogsByUserId(Long userId, Integer pageNo, Integer pageSize);
}
