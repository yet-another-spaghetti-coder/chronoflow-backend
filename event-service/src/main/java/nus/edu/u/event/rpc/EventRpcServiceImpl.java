package nus.edu.u.event.rpc;

import static nus.edu.u.common.enums.ErrorCodeConstants.EVENT_NOT_FOUND;
import static nus.edu.u.common.utils.exception.ServiceExceptionUtil.exception;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import nus.edu.u.event.convert.EventConvert;
import nus.edu.u.event.domain.dataobject.event.EventDO;
import nus.edu.u.event.domain.dataobject.user.UserGroupDO;
import nus.edu.u.event.domain.dto.event.EventRespVO;
import nus.edu.u.event.domain.dto.group.GroupRespVO;
import nus.edu.u.event.mapper.EventMapper;
import nus.edu.u.event.mapper.UserGroupMapper;
import nus.edu.u.event.service.GroupApplicationService;
import nus.edu.u.shared.rpc.events.EventRespDTO;
import nus.edu.u.shared.rpc.events.EventRpcService;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
@RequiredArgsConstructor
public class EventRpcServiceImpl implements EventRpcService {
    private final EventConvert eventConvert;
    private final EventMapper eventMapper;
    private final UserGroupMapper userGroupMapper;
    private final GroupApplicationService groupApplicationService;

    @Override
    public String getEventName(Long eventId) {
        EventDO eventDO = eventMapper.selectById(eventId);
        return eventDO.getName();
    }

    @Override
    public EventRespDTO getEvent(Long eventId) {
        EventDO event = eventMapper.selectById(eventId);
        if (event == null) {
            throw exception(EVENT_NOT_FOUND);
        }

        EventRespVO eventResp = eventConvert.DOconvertVO(event);
        if (eventResp == null) {
            eventResp = fallbackEventResp(event);
        }

        eventResp.setJoiningParticipants(countParticipants(eventId));
        eventResp.setGroups(fetchGroupSummaries(eventId));

        return eventConvert.toRpc(eventResp);
    }

    @Override
    public boolean exists(Long eventId) {
        if (eventId == null) {
            return false;
        }
        return eventMapper.selectById(eventId) != null;
    }

    private EventRespVO fallbackEventResp(EventDO event) {
        EventRespVO response = new EventRespVO();
        response.setId(event.getId());
        response.setName(event.getName());
        response.setDescription(event.getDescription());
        response.setOrganizerId(event.getUserId());
        response.setLocation(event.getLocation());
        response.setStartTime(event.getStartTime());
        response.setEndTime(event.getEndTime());
        response.setStatus(event.getStatus());
        response.setRemark(event.getRemark());
        response.setCreateTime(event.getCreateTime());
        return response;
    }

    private int countParticipants(Long eventId) {
        return userGroupMapper
                .selectList(
                        new LambdaQueryWrapper<UserGroupDO>().eq(UserGroupDO::getEventId, eventId))
                .stream()
                .map(UserGroupDO::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
                .size();
    }

    private List<EventRespVO.GroupVO> fetchGroupSummaries(Long eventId) {
        List<GroupRespVO> groups = groupApplicationService.getGroupsByEvent(eventId);
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }
        return groups.stream()
                .map(
                        group -> {
                            EventRespVO.GroupVO groupVO = new EventRespVO.GroupVO();
                            groupVO.setId(
                                    group.getId() != null ? String.valueOf(group.getId()) : null);
                            groupVO.setName(group.getName());
                            return groupVO;
                        })
                .toList();
    }
}
