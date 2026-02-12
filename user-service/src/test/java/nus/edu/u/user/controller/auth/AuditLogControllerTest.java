package nus.edu.u.user.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import nus.edu.u.common.core.domain.CommonResult;
import nus.edu.u.common.core.domain.PageResult;
import nus.edu.u.user.domain.vo.audit.AuditLogQueryReqVO;
import nus.edu.u.user.domain.vo.audit.AuditLogRespVO;
import nus.edu.u.user.service.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock private AuditLogService auditLogService;

    @InjectMocks private AuditLogController controller;

    private AuditLogRespVO sampleResp;

    @BeforeEach
    void setUp() {
        sampleResp = AuditLogRespVO.builder()
                .id(1L)
                .userId(100L)
                .module("user")
                .operation("Create Role")
                .type(2)
                .method("POST")
                .requestUrl("/users/roles")
                .targetType("Role")
                .targetId("5")
                .resultCode(0)
                .duration(42)
                .createTime(LocalDateTime.of(2025, 12, 1, 10, 30))
                .build();
    }

    @Test
    void getAuditLogs_returnsPaginatedResults() {
        AuditLogQueryReqVO reqVO = new AuditLogQueryReqVO();
        PageResult<AuditLogRespVO> pageResult = new PageResult<>(List.of(sampleResp), 1L);
        when(auditLogService.getAuditLogPage(any(AuditLogQueryReqVO.class))).thenReturn(pageResult);

        CommonResult<PageResult<AuditLogRespVO>> result = controller.getAuditLogs(reqVO);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTotal()).isEqualTo(1);
        assertThat(result.getData().getList()).hasSize(1);
        assertThat(result.getData().getList().get(0).getOperation()).isEqualTo("Create Role");
    }

    @Test
    void getAuditLog_byId_returnsEntry() {
        when(auditLogService.getAuditLog(1L)).thenReturn(sampleResp);

        CommonResult<AuditLogRespVO> result = controller.getAuditLog(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getId()).isEqualTo(1L);
        assertThat(result.getData().getModule()).isEqualTo("user");
    }

    @Test
    void getAuditLog_notFound_returnsNull() {
        when(auditLogService.getAuditLog(999L)).thenReturn(null);

        CommonResult<AuditLogRespVO> result = controller.getAuditLog(999L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNull();
    }

    @Test
    void getAuditLogsByUser_returnsPaginatedResults() {
        PageResult<AuditLogRespVO> pageResult = new PageResult<>(List.of(sampleResp), 1L);
        when(auditLogService.getAuditLogsByUserId(eq(100L), eq(1), eq(20))).thenReturn(pageResult);

        CommonResult<PageResult<AuditLogRespVO>> result =
                controller.getAuditLogsByUser(100L, 1, 20);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTotal()).isEqualTo(1);
        assertThat(result.getData().getList().get(0).getUserId()).isEqualTo(100L);
    }

    @Test
    void getAuditLogsByUser_defaultPagination() {
        PageResult<AuditLogRespVO> pageResult = new PageResult<>(List.of(), 0L);
        when(auditLogService.getAuditLogsByUserId(eq(200L), eq(1), eq(20))).thenReturn(pageResult);

        CommonResult<PageResult<AuditLogRespVO>> result =
                controller.getAuditLogsByUser(200L, 1, 20);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getList()).isEmpty();
    }
}
