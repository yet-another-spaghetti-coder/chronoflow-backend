package nus.edu.u.framework.security.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis Plus mapper for {@link AuditLogDO}.
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogDO> {
}
