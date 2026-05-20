package nus.edu.u.attendee.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import nus.edu.u.attendee.domain.dataobject.EventAttendeeDO;
import nus.edu.u.attendee.domain.vo.attendee.AttendeeSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Event Attendee Mapper
 *
 * @author Fan Yazhuoting
 * @date 2025-10-07
 */
@Mapper
public interface EventAttendeeMapper extends BaseMapper<EventAttendeeDO> {

    @Select(
"""
  SELECT
    COALESCE(SUM(CASE WHEN check_in_status = 1 THEN 1 ELSE 0 END), 0) AS checkedIn,
    COALESCE(SUM(CASE WHEN check_in_status = 0 THEN 1 ELSE 0 END), 0) AS nonCheckedIn
  FROM event_attendee
  WHERE event_id = #{eventId}
    AND tenant_id = #{tenantId}
    AND deleted = b'0'
""")
    AttendeeSummaryVO selectCheckInSummary(
            @Param("eventId") Long eventId, @Param("tenantId") Long tenantId);

    default Page<EventAttendeeDO> selectNotCheckedInPage(
            Page<EventAttendeeDO> page, Long eventId, Long tenantId) {

        LambdaQueryWrapper<EventAttendeeDO> qw =
                new LambdaQueryWrapper<EventAttendeeDO>()
                        .eq(EventAttendeeDO::getEventId, eventId)
                        .eq(EventAttendeeDO::getTenantId, tenantId)
                        .eq(EventAttendeeDO::getDeleted, false)
                        .eq(EventAttendeeDO::getCheckInStatus, 0)
                        .orderByDesc(EventAttendeeDO::getCreateTime);

        return this.selectPage(page, qw);
    }

    /** 通过 token 查询 */
    default EventAttendeeDO selectByToken(String token) {
        return this.selectOne(
                Wrappers.<EventAttendeeDO>lambdaQuery()
                        .eq(EventAttendeeDO::getCheckInToken, token)
                        .eq(EventAttendeeDO::getDeleted, false)
                        .last("LIMIT 1"));
    }

    /** 通过 eventId 和 email 查询 */
    default EventAttendeeDO selectByEventAndEmail(Long eventId, String email) {
        return this.selectOne(
                Wrappers.<EventAttendeeDO>lambdaQuery()
                        .eq(EventAttendeeDO::getEventId, eventId)
                        .eq(EventAttendeeDO::getAttendeeEmail, email)
                        .eq(EventAttendeeDO::getDeleted, false)
                        .last("LIMIT 1"));
    }

    /** 通过 eventId 查询所有 attendees */
    default List<EventAttendeeDO> selectByEventId(Long eventId) {
        return this.selectList(
                Wrappers.<EventAttendeeDO>lambdaQuery().eq(EventAttendeeDO::getEventId, eventId));
    }
}
