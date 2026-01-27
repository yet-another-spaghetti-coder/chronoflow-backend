package nus.edu.u.event.service;

import static nus.edu.u.common.enums.ErrorCodeConstants.*;
import static nus.edu.u.common.utils.exception.ServiceExceptionUtil.exception;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.common.enums.EventStatusEnum;
import nus.edu.u.event.convert.EventConvert;
import nus.edu.u.event.domain.dataobject.event.EventDO;
import nus.edu.u.event.domain.dataobject.user.UserGroupDO;
import nus.edu.u.event.domain.dto.event.EventCreateReqVO;
import nus.edu.u.event.domain.dto.event.EventGroupRespVO;
import nus.edu.u.event.domain.dto.event.EventRespVO;
import nus.edu.u.event.domain.dto.event.EventUpdateReqVO;
import nus.edu.u.event.domain.dto.event.UpdateEventRespVO;
import nus.edu.u.event.domain.dto.group.GroupRespVO;
import nus.edu.u.event.enums.TaskStatusEnum;
import nus.edu.u.event.mapper.EventMapper;
import nus.edu.u.event.mapper.UserGroupMapper;
import nus.edu.u.event.service.validation.EventValidationContext;
import nus.edu.u.event.service.validation.EventValidationHandler;
import nus.edu.u.shared.rpc.group.GroupDTO;
import nus.edu.u.shared.rpc.task.TaskDTO;
import nus.edu.u.shared.rpc.task.TaskRpcService;
import nus.edu.u.shared.rpc.user.UserRpcService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EventApplicationServiceImpl implements EventApplicationService {

    private final EventMapper eventMapper;
    private final UserGroupMapper userGroupMapper;
    private final EventConvert eventConvert;
    private final GroupApplicationService groupApplicationService;
    private final List<EventValidationHandler> validationHandlers;

    @DubboReference(check = false)
    private UserRpcService userRpcService;

    @DubboReference(check = false)
    private TaskRpcService taskRpcService;

    @Override
    public EventRespVO createEvent(EventCreateReqVO reqVO) {
        runValidations(EventValidationContext.forCreate(reqVO));
        EventDO event = prepareForCreate(reqVO);
        eventMapper.insert(event);
        EventDO persisted = eventMapper.selectById(event.getId());
        return toResponse(persisted);
    }

    @Override
    @Transactional(readOnly = true)
    public EventRespVO getEvent(Long eventId) {
        EventDO event = eventMapper.selectById(eventId);
        if (event == null) {
            throw exception(EVENT_NOT_FOUND);
        }
        return toResponse(event);
    }

    @Override
    @Transactional
    public List<EventRespVO> list() {
        Long organizerId = StpUtil.getLoginIdAsLong();
        if (!userRpcService.exists(organizerId)) {
            throw exception(EVENT_NOT_FOUND);
        }

        List<EventDO> organizerEvents =
                eventMapper.selectList(
                        new LambdaQueryWrapper<EventDO>().eq(EventDO::getUserId, organizerId));

        List<UserGroupDO> participantRecords =
                userGroupMapper.selectList(
                        new LambdaQueryWrapper<UserGroupDO>()
                                .eq(UserGroupDO::getUserId, organizerId));

        Map<Long, EventDO> eventsById = new LinkedHashMap<>();
        if (organizerEvents != null) {
            organizerEvents.stream()
                    .filter(Objects::nonNull)
                    .forEach(event -> eventsById.put(event.getId(), event));
        }
        if (participantRecords != null && !participantRecords.isEmpty()) {
            Set<Long> participantEventIds =
                    participantRecords.stream()
                            .map(UserGroupDO::getEventId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
            participantEventIds.removeAll(eventsById.keySet());
            if (!participantEventIds.isEmpty()) {
                List<EventDO> participantEvents = eventMapper.selectBatchIds(participantEventIds);
                if (participantEvents != null) {
                    participantEvents.stream()
                            .filter(Objects::nonNull)
                            .forEach(event -> eventsById.put(event.getId(), event));
                }
            }
        }

        if (eventsById.isEmpty()) {
            return List.of();
        }

        Comparator<EventDO> byCreateTimeDesc =
                Comparator.comparing(
                                (EventDO event) ->
                                        Optional.ofNullable(event.getCreateTime())
                                                .orElse(LocalDateTime.MIN))
                        .reversed();
        Comparator<EventDO> byIdDesc =
                Comparator.comparing(
                                (EventDO event) ->
                                        Optional.ofNullable(event.getId()).orElse(Long.MIN_VALUE))
                        .reversed();

        List<EventDO> orderedEvents =
                eventsById.values().stream()
                        .sorted(byCreateTimeDesc.thenComparing(byIdDesc))
                        .toList();

        List<Long> eventIds = orderedEvents.stream().map(EventDO::getId).toList();
        Map<Long, Integer> countsByEventId = fetchParticipantCountsByEventIds(eventIds);
        Map<Long, List<EventRespVO.GroupVO>> groupsByEventId = fetchGroupsByEventIds(eventIds);
        Map<Long, EventRespVO.TaskStatusVO> taskStatusByEventId =
                fetchTaskStatusesByEventIds(eventIds);

        updateStatusesIfNecessary(orderedEvents);

        return orderedEvents.stream()
                .map(
                        event ->
                                toResponse(
                                        event,
                                        countsByEventId,
                                        groupsByEventId,
                                        taskStatusByEventId))
                .toList();
    }

    @Override
    public UpdateEventRespVO updateEvent(Long id, EventUpdateReqVO reqVO) {
        EventDO current = eventMapper.selectById(id);
        if (current == null) {
            throw exception(EVENT_NOT_FOUND);
        }

        runValidations(EventValidationContext.forUpdate(reqVO, current));

        eventConvert.patch(current, reqVO);
        eventMapper.updateById(current);

        EventDO updated = eventMapper.selectById(id);
        UpdateEventRespVO resp = eventConvert.toUpdateResp(updated);
        if (resp != null) {
            resp.setParticipantUserIds(fetchParticipantIdsByEventId(id));
            resp.setUpdateTime(updated.getUpdateTime());
        }
        return resp;
    }

    @Override
    public boolean deleteEvent(Long id) {
        EventDO db = eventMapper.selectById(id);
        if (db == null) {
            throw exception(EVENT_NOT_FOUND);
        }
        int rows = eventMapper.deleteById(id);
        if (rows <= 0) {
            throw exception(EVENT_DELETE_FAILED);
        }
        if (taskRpcService != null) {
            try {
                taskRpcService.deleteTasksByEventId(id);
            } catch (Exception ex) {
                log.error("Failed to delete tasks for event {}", id, ex);
                throw exception(TASK_DELETE_FAILED);
            }
        } else {
            log.warn("TaskRpcService unavailable, skipped deleting tasks for event {}", id);
        }
        userGroupMapper.delete(
                new LambdaQueryWrapper<UserGroupDO>().eq(UserGroupDO::getEventId, id));
        return true;
    }

    @Override
    public boolean restoreEvent(Long id) {
        EventDO db = eventMapper.selectRawById(id);
        if (db == null) {
            throw exception(EVENT_NOT_FOUND);
        }
        if (Boolean.FALSE.equals(db.getDeleted())) {
            throw exception(EVENT_NOT_DELETED);
        }
        int rows = eventMapper.restoreById(id);
        if (rows <= 0) {
            throw exception(EVENT_RESTORE_FAILED);
        }
        userGroupMapper.restoreByEventId(id);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventGroupRespVO> findAssignableGroups(Long eventId) {
        Map<Long, List<GroupDTO>> groups =
                groupApplicationService.getGroupDTOsByEventIds(List.of(eventId));
        List<GroupDTO> summaries = groups.getOrDefault(eventId, List.of());
        if (summaries.isEmpty()) {
            return List.of();
        }
        List<EventGroupRespVO> result = new ArrayList<>();
        for (GroupDTO summary : summaries) {
            EventGroupRespVO resp = new EventGroupRespVO();
            resp.setId(summary.getId());
            resp.setName(summary.getName());
            List<EventGroupRespVO.Member> members =
                    summary.getMembers() == null
                            ? List.of()
                            : summary.getMembers().stream()
                                    .map(
                                            member ->
                                                    new EventGroupRespVO.Member(
                                                            member.getUserId(),
                                                            member.getUsername()))
                                    .toList();
            resp.setMembers(members);
            result.add(resp);
        }
        return result;
    }

    private void runValidations(EventValidationContext context) {
        if (validationHandlers != null && !validationHandlers.isEmpty()) {
            for (EventValidationHandler handler : validationHandlers) {
                if (handler.supports(context)) {
                    handler.validate(context);
                }
            }
        }
    }

    private EventDO prepareForCreate(EventCreateReqVO reqVO) {
        var dto = eventConvert.convert(reqVO);
        EventDO event = eventConvert.convert(dto);
        if (event.getStatus() == null) {
            event.setStatus(EventStatusEnum.ACTIVE.getCode());
        }
        return event;
    }

    private void updateStatusesIfNecessary(List<EventDO> events) {
        LocalDateTime now = LocalDateTime.now();
        for (EventDO event : events) {
            if (event.getStartTime() == null || event.getEndTime() == null) {
                continue;
            }
            if (now.isBefore(event.getStartTime())) {
                event.setStatus(EventStatusEnum.NOT_STARTED.getCode());
            } else if (now.isAfter(event.getEndTime())) {
                event.setStatus(EventStatusEnum.COMPLETED.getCode());
            } else {
                event.setStatus(EventStatusEnum.ACTIVE.getCode());
            }
            eventMapper.updateById(event);
        }
    }

    private EventRespVO toResponse(EventDO event) {
        Map<Long, Integer> counts =
                fetchParticipantCountsByEventIds(Collections.singletonList(event.getId()));
        Map<Long, List<EventRespVO.GroupVO>> groups =
                fetchGroupsByEventIds(Collections.singletonList(event.getId()));
        Map<Long, EventRespVO.TaskStatusVO> taskStatus =
                fetchTaskStatusesByEventIds(Collections.singletonList(event.getId()));
        return toResponse(event, counts, groups, taskStatus);
    }

    private EventRespVO toResponse(
            EventDO event,
            Map<Long, Integer> participantCounts,
            Map<Long, List<EventRespVO.GroupVO>> groupsByEventId,
            Map<Long, EventRespVO.TaskStatusVO> taskStatusByEventId) {
        EventRespVO response = eventConvert.DOconvertVO(event);
        if (response == null) {
            response = new EventRespVO();
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
        }
        response.setJoiningParticipants(participantCounts.getOrDefault(event.getId(), 0));
        response.setGroups(groupsByEventId.getOrDefault(event.getId(), List.of()));
        response.setTaskStatus(taskStatusByEventId.getOrDefault(event.getId(), emptyTaskStatus()));
        return response;
    }

    private Map<Long, Integer> fetchParticipantCountsByEventIds(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Map.of();
        }
        return userGroupMapper
                .selectList(
                        new LambdaQueryWrapper<UserGroupDO>().in(UserGroupDO::getEventId, eventIds))
                .stream()
                .filter(relation -> relation.getEventId() != null && relation.getUserId() != null)
                .collect(
                        Collectors.groupingBy(
                                UserGroupDO::getEventId,
                                Collectors.collectingAndThen(
                                        Collectors.mapping(
                                                UserGroupDO::getUserId, Collectors.toSet()),
                                        Set::size)));
    }

    private List<Long> fetchParticipantIdsByEventId(Long eventId) {
        if (eventId == null) {
            return List.of();
        }
        return userGroupMapper
                .selectList(
                        new LambdaQueryWrapper<UserGroupDO>().eq(UserGroupDO::getEventId, eventId))
                .stream()
                .map(UserGroupDO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Map<Long, List<EventRespVO.GroupVO>> fetchGroupsByEventIds(List<Long> eventIds) {
        Map<Long, List<GroupRespVO>> groups = groupApplicationService.getGroupsByEventIds(eventIds);
        Map<Long, List<EventRespVO.GroupVO>> result = new HashMap<>();
        for (Long eventId : eventIds) {
            List<EventRespVO.GroupVO> groupVOs =
                    groups.getOrDefault(eventId, List.of()).stream()
                            .map(
                                    summary -> {
                                        EventRespVO.GroupVO vo = new EventRespVO.GroupVO();
                                        vo.setId(
                                                summary.getId() != null
                                                        ? summary.getId().toString()
                                                        : null);
                                        vo.setName(summary.getName());
                                        return vo;
                                    })
                            .toList();
            result.put(eventId, groupVOs);
        }
        return result;
    }

    private Map<Long, EventRespVO.TaskStatusVO> fetchTaskStatusesByEventIds(List<Long> eventIds) {
        Map<Long, List<TaskDTO>> tasksByEvent = taskRpcService.getTasksByEventIds(eventIds);
        Map<Long, EventRespVO.TaskStatusVO> result = new HashMap<>();
        for (Long eventId : eventIds) {
            List<TaskDTO> tasks = tasksByEvent.getOrDefault(eventId, List.of());
            if (tasks.isEmpty()) {
                result.put(eventId, emptyTaskStatus());
                continue;
            }
            EventRespVO.TaskStatusVO statusVO = new EventRespVO.TaskStatusVO();
            statusVO.setTotal(tasks.size());
            long completed =
                    tasks.stream()
                            .map(TaskDTO::getStatus)
                            .map(TaskStatusEnum::fromStatus)
                            .filter(Objects::nonNull)
                            .filter(status -> status == TaskStatusEnum.COMPLETED)
                            .count();
            statusVO.setCompleted((int) completed);
            statusVO.setRemaining(tasks.size() - (int) completed);
            result.put(eventId, statusVO);
        }
        return result;
    }

    private EventRespVO.TaskStatusVO emptyTaskStatus() {
        EventRespVO.TaskStatusVO statusVO = new EventRespVO.TaskStatusVO();
        statusVO.setTotal(0);
        statusVO.setCompleted(0);
        statusVO.setRemaining(0);
        return statusVO;
    }
}
