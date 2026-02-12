package nus.edu.u.user.service.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import java.util.List;
import nus.edu.u.common.core.domain.PageResult;
import nus.edu.u.framework.security.audit.AuditLogDO;
import nus.edu.u.framework.security.audit.AuditLogMapper;
import nus.edu.u.user.domain.vo.audit.AuditLogQueryReqVO;
import nus.edu.u.user.domain.vo.audit.AuditLogRespVO;
import org.springframework.stereotype.Service;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    @Resource
    private AuditLogMapper auditLogMapper;

    @Override
    public PageResult<AuditLogRespVO> getAuditLogPage(AuditLogQueryReqVO reqVO) {
        LambdaQueryWrapper<AuditLogDO> wrapper = buildWrapper(reqVO);
        wrapper.orderByDesc(AuditLogDO::getCreateTime);

        IPage<AuditLogDO> page = auditLogMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), wrapper);

        List<AuditLogRespVO> list = page.getRecords().stream().map(this::convert).toList();
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public AuditLogRespVO getAuditLog(Long id) {
        AuditLogDO auditLog = auditLogMapper.selectById(id);
        return auditLog != null ? convert(auditLog) : null;
    }

    @Override
    public PageResult<AuditLogRespVO> getAuditLogsByUserId(Long userId, Integer pageNo, Integer pageSize) {
        LambdaQueryWrapper<AuditLogDO> wrapper = new LambdaQueryWrapper<AuditLogDO>()
                .eq(AuditLogDO::getUserId, userId)
                .orderByDesc(AuditLogDO::getCreateTime);

        IPage<AuditLogDO> page = auditLogMapper.selectPage(
                new Page<>(pageNo, pageSize), wrapper);

        List<AuditLogRespVO> list = page.getRecords().stream().map(this::convert).toList();
        return new PageResult<>(list, page.getTotal());
    }

    private LambdaQueryWrapper<AuditLogDO> buildWrapper(AuditLogQueryReqVO reqVO) {
        LambdaQueryWrapper<AuditLogDO> wrapper = new LambdaQueryWrapper<>();
        if (reqVO.getUserId() != null) {
            wrapper.eq(AuditLogDO::getUserId, reqVO.getUserId());
        }
        if (reqVO.getModule() != null && !reqVO.getModule().isEmpty()) {
            wrapper.eq(AuditLogDO::getModule, reqVO.getModule());
        }
        if (reqVO.getType() != null) {
            wrapper.eq(AuditLogDO::getType, reqVO.getType());
        }
        if (reqVO.getOperation() != null && !reqVO.getOperation().isEmpty()) {
            wrapper.like(AuditLogDO::getOperation, reqVO.getOperation());
        }
        if (reqVO.getTargetType() != null && !reqVO.getTargetType().isEmpty()) {
            wrapper.eq(AuditLogDO::getTargetType, reqVO.getTargetType());
        }
        if (reqVO.getStartTime() != null) {
            wrapper.ge(AuditLogDO::getCreateTime, reqVO.getStartTime());
        }
        if (reqVO.getEndTime() != null) {
            wrapper.le(AuditLogDO::getCreateTime, reqVO.getEndTime());
        }
        return wrapper;
    }

    private AuditLogRespVO convert(AuditLogDO entity) {
        return AuditLogRespVO.builder()
                .id(entity.getId())
                .traceId(entity.getTraceId())
                .userId(entity.getUserId())
                .userIp(entity.getUserIp())
                .userAgent(entity.getUserAgent())
                .module(entity.getModule())
                .operation(entity.getOperation())
                .type(entity.getType())
                .method(entity.getMethod())
                .requestUrl(entity.getRequestUrl())
                .requestBody(entity.getRequestBody())
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .beforeData(entity.getBeforeData())
                .afterData(entity.getAfterData())
                .resultCode(entity.getResultCode())
                .resultMsg(entity.getResultMsg())
                .duration(entity.getDuration())
                .extra(entity.getExtra())
                .createTime(entity.getCreateTime())
                .build();
    }
}
