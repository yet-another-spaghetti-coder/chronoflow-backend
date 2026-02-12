package nus.edu.u.user.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import nus.edu.u.common.core.domain.PageResult;
import nus.edu.u.framework.security.audit.AuditLogDO;
import nus.edu.u.framework.security.audit.AuditLogMapper;
import nus.edu.u.user.domain.vo.audit.AuditLogQueryReqVO;
import nus.edu.u.user.domain.vo.audit.AuditLogRespVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogServiceImplTest {

    @Mock private AuditLogMapper auditLogMapper;

    @InjectMocks private AuditLogServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AuditLogDO.class);
    }

    private AuditLogDO sampleLog;

    @BeforeEach
    void setUp() {
        sampleLog = AuditLogDO.builder()
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
                .build();
        sampleLog.setCreateTime(LocalDateTime.of(2025, 12, 1, 10, 30));
    }

    @Test
    void getAuditLogPage_returnsPagedResults() {
        AuditLogQueryReqVO reqVO = new AuditLogQueryReqVO();
        reqVO.setModule("user");
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);

        Page<AuditLogDO> page = new Page<>(1, 10);
        page.setRecords(List.of(sampleLog));
        page.setTotal(1);
        when(auditLogMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        PageResult<AuditLogRespVO> result = service.getAuditLogPage(reqVO);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList()).hasSize(1);
        AuditLogRespVO vo = result.getList().get(0);
        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getModule()).isEqualTo("user");
        assertThat(vo.getOperation()).isEqualTo("Create Role");
        assertThat(vo.getTargetType()).isEqualTo("Role");
        assertThat(vo.getTargetId()).isEqualTo("5");
    }

    @Test
    void getAuditLogPage_withAllFilters() {
        AuditLogQueryReqVO reqVO = new AuditLogQueryReqVO();
        reqVO.setUserId(100L);
        reqVO.setModule("user");
        reqVO.setType(2);
        reqVO.setOperation("Create");
        reqVO.setTargetType("Role");
        reqVO.setStartTime(LocalDateTime.of(2025, 1, 1, 0, 0));
        reqVO.setEndTime(LocalDateTime.of(2025, 12, 31, 23, 59));
        reqVO.setPageNo(1);
        reqVO.setPageSize(20);

        Page<AuditLogDO> page = new Page<>(1, 20);
        page.setRecords(List.of(sampleLog));
        page.setTotal(1);
        when(auditLogMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        PageResult<AuditLogRespVO> result = service.getAuditLogPage(reqVO);

        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void getAuditLogPage_emptyResult() {
        AuditLogQueryReqVO reqVO = new AuditLogQueryReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);

        Page<AuditLogDO> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(auditLogMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        PageResult<AuditLogRespVO> result = service.getAuditLogPage(reqVO);

        assertThat(result.getTotal()).isEqualTo(0);
        assertThat(result.getList()).isEmpty();
    }

    @Test
    void getAuditLog_existingId_returnsLog() {
        when(auditLogMapper.selectById(1L)).thenReturn(sampleLog);

        AuditLogRespVO result = service.getAuditLog(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getOperation()).isEqualTo("Create Role");
        assertThat(result.getDuration()).isEqualTo(42);
    }

    @Test
    void getAuditLog_nonExistingId_returnsNull() {
        when(auditLogMapper.selectById(999L)).thenReturn(null);

        AuditLogRespVO result = service.getAuditLog(999L);

        assertThat(result).isNull();
    }

    @Test
    void getAuditLogsByUserId_returnsUserLogs() {
        Page<AuditLogDO> page = new Page<>(1, 20);
        page.setRecords(List.of(sampleLog));
        page.setTotal(1);
        when(auditLogMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        PageResult<AuditLogRespVO> result = service.getAuditLogsByUserId(100L, 1, 20);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getUserId()).isEqualTo(100L);
    }
}
