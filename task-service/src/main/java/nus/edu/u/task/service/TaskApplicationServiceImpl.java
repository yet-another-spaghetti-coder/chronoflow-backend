package nus.edu.u.task.service;

import static nus.edu.u.common.enums.ErrorCodeConstants.*;
import static nus.edu.u.common.utils.exception.ServiceExceptionUtil.exception;
import static nus.edu.u.task.enums.TaskActionEnum.getUpdateTaskAction;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.framework.security.audit.AuditType;
import nus.edu.u.framework.security.audit.Auditable;
import nus.edu.u.shared.rpc.events.EventRespDTO;
import nus.edu.u.shared.rpc.events.EventRpcService;
import nus.edu.u.shared.rpc.group.GroupDTO;
import nus.edu.u.shared.rpc.group.GroupMemberDTO;
import nus.edu.u.shared.rpc.group.GroupRpcService;
import nus.edu.u.shared.rpc.notification.dto.task.NewTaskAssignmentDTO;
import nus.edu.u.shared.rpc.user.UserInfoDTO;
import nus.edu.u.shared.rpc.user.UserRpcService;
import nus.edu.u.task.action.TaskActionFactory;
import nus.edu.u.task.convert.TaskConvert;
import nus.edu.u.task.domain.dataobject.group.DeptDO;
import nus.edu.u.task.domain.dataobject.task.TaskDO;
import nus.edu.u.task.domain.dataobject.user.UserDO;
import nus.edu.u.task.domain.dto.TaskActionDTO;
import nus.edu.u.task.domain.vo.task.TaskCreateReqVO;
import nus.edu.u.task.domain.vo.task.TaskDashboardRespVO;
import nus.edu.u.task.domain.vo.task.TaskRespVO;
import nus.edu.u.task.domain.vo.task.TaskUpdateReqVO;
import nus.edu.u.task.domain.vo.task.TasksRespVO;
import nus.edu.u.task.enums.TaskActionEnum;
import nus.edu.u.task.mapper.TaskMapper;
import nus.edu.u.task.publisher.TaskNotificationPublisher;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TaskApplicationServiceImpl implements TaskApplicationService {

    private final TaskMapper taskMapper;

    @DubboReference(check = false)
    private EventRpcService eventRpcService;

    @DubboReference(check = false)
    private UserRpcService userRpcService;

    @DubboReference(check = false)
    private GroupRpcService groupRpcService;

    private final TaskActionFactory taskActionFactory;

    private final TaskNotificationPublisher taskNotificationPublisher;

    @Override
    @Transactional
    @Auditable(operation = "Create Task", type = AuditType.DATA_CHANGE,
               targetType = "Task", targetId = "#eventId")
    public TaskRespVO createTask(Long eventId, TaskCreateReqVO reqVO) {
        EventRespDTO event = eventRpcService.getEvent(eventId);
        if (event == null) {
            throw exception(EVENT_NOT_FOUND);
        }

        Long assignerId = event.getOrganizerId();
        Long assigneeId = reqVO.getTargetUserId();

        Set<Long> userIdsToLoad = new LinkedHashSet<>();
        if (assignerId != null) {
            userIdsToLoad.add(assignerId);
        }
        if (assigneeId != null) {
            userIdsToLoad.add(assigneeId);
        }

        Map<Long, UserDO> usersById = loadUsers(userIdsToLoad);
        UserDO assignee = assigneeId != null ? usersById.get(assigneeId) : null;
        if (assignee == null) {
            throw exception(TASK_ASSIGNEE_NOT_FOUND);
        }

        UserDO assigner = assignerId != null ? usersById.get(assignerId) : null;
        Long eventTenantId = assigner != null ? assigner.getTenantId() : null;
        if (eventTenantId != null && !Objects.equals(eventTenantId, assignee.getTenantId())) {
            throw exception(TASK_ASSIGNEE_TENANT_MISMATCH);
        }

        TaskDO task = TaskConvert.INSTANCE.convert(reqVO);
        task.setEventId(eventId);
        task.setTenantId(eventTenantId);
        task.setStartTime(reqVO.getStartTime());
        task.setEndTime(reqVO.getEndTime());

        TaskActionDTO actionDTO =
                TaskActionDTO.builder()
                        .startTime(reqVO.getStartTime())
                        .endTime(reqVO.getEndTime())
                        .files(reqVO.getFiles())
                        .targetUserId(reqVO.getTargetUserId())
                        .eventStartTime(event.getStartTime())
                        .eventEndTime(event.getEndTime())
                        .build();
        taskActionFactory.getStrategy(TaskActionEnum.CREATE).execute(task, actionDTO);

        NewTaskAssignmentDTO dto =
                NewTaskAssignmentDTO.builder()
                        .taskId(String.valueOf(task.getId()))
                        .eventId(String.valueOf(event.getId()))
                        .assigneeUserId(String.valueOf(assignee.getId()))
                        .assigneeEmail(assignee.getEmail())
                        .assignerName(assigner != null ? assigner.getUsername() : "System")
                        .taskName(task.getName())
                        .eventName(event.getName())
                        .description(task.getDescription())
                        .build();

        taskNotificationPublisher.notifyNewTaskToAssigneeEmail(dto);
        taskNotificationPublisher.notifyNewTaskToAssigneePush(dto);
        taskNotificationPublisher.notifyNewTaskToAssigneeWs(dto);

        Map<Long, List<GroupDTO>> groupsByEvent = preloadGroups(List.of(eventId));
        Map<Long, List<DeptDO>> deptsByUser =
                userIdsToLoad.isEmpty()
                        ? Map.of()
                        : fetchUserDeptsByEvents(userIdsToLoad, List.of(eventId), groupsByEvent);
        List<DeptDO> assignerDepts =
                assignerId != null ? deptsByUser.getOrDefault(assignerId, List.of()) : List.of();
        List<DeptDO> assigneeDepts =
                assigneeId != null ? deptsByUser.getOrDefault(assigneeId, List.of()) : List.of();

        return toTaskRespVO(task, assigner, assignerDepts, assignee, assigneeDepts);
    }

    @Override
    @Transactional
    @Auditable(operation = "Update Task", type = AuditType.DATA_CHANGE,
               targetType = "Task", targetId = "#taskId")
    public TaskRespVO updateTask(Long eventId, Long taskId, TaskUpdateReqVO reqVO, Integer type) {
        EventRespDTO event = eventRpcService.getEvent(eventId);
        if (event == null) {
            throw exception(EVENT_NOT_FOUND);
        }

        TaskDO task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getEventId(), eventId)) {
            throw exception(TASK_NOT_FOUND);
        }

        Long assignerId = event.getOrganizerId();
        Long currentAssigneeId = task.getUserId();
        Long targetAssigneeId = reqVO.getTargetUserId();

        Set<Long> userIdsToLoad = new LinkedHashSet<>();
        if (assignerId != null) {
            userIdsToLoad.add(assignerId);
        }
        if (currentAssigneeId != null) {
            userIdsToLoad.add(currentAssigneeId);
        }
        if (targetAssigneeId != null) {
            userIdsToLoad.add(targetAssigneeId);
        }

        Map<Long, UserDO> usersById = loadUsers(userIdsToLoad);

        UserDO assigner = assignerId != null ? usersById.get(assignerId) : null;
        Long eventTenantId = assigner != null ? assigner.getTenantId() : null;

        UserDO targetAssignee = null;
        if (targetAssigneeId != null) {
            targetAssignee = usersById.get(targetAssigneeId);
            if (targetAssignee == null) {
                throw exception(TASK_ASSIGNEE_NOT_FOUND);
            }

            if (eventTenantId != null
                    && !Objects.equals(eventTenantId, targetAssignee.getTenantId())) {
                throw exception(TASK_ASSIGNEE_TENANT_MISMATCH);
            }
        }

        if (!Arrays.asList(getUpdateTaskAction()).contains(reqVO.getType())) {
            throw exception(WRONG_TASK_ACTION_TYPE);
        }
        TaskActionDTO actionDTO =
                TaskActionDTO.builder()
                        .name(reqVO.getName())
                        .description(reqVO.getDescription())
                        .startTime(reqVO.getStartTime())
                        .endTime(reqVO.getEndTime())
                        .eventStartTime(event.getStartTime())
                        .eventEndTime(event.getEndTime())
                        .targetUserId(reqVO.getTargetUserId())
                        .files(reqVO.getFiles())
                        .remark(reqVO.getRemark())
                        .build();

        taskActionFactory.getStrategy(TaskActionEnum.getEnum(type)).execute(task, actionDTO);

        Long finalAssigneeId = task.getUserId();
        UserDO finalAssignee = finalAssigneeId != null ? usersById.get(finalAssigneeId) : null;
        if (finalAssignee == null && targetAssigneeId != null) {
            finalAssignee = usersById.get(targetAssigneeId);
        }

        Set<Long> groupUserIds = new LinkedHashSet<>();
        if (assignerId != null) {
            groupUserIds.add(assignerId);
        }
        if (finalAssigneeId != null) {
            groupUserIds.add(finalAssigneeId);
        }

        Map<Long, List<GroupDTO>> groupsByEvent = preloadGroups(List.of(eventId));
        Map<Long, List<DeptDO>> deptsByUser =
                groupUserIds.isEmpty()
                        ? Map.of()
                        : fetchUserDeptsByEvents(groupUserIds, List.of(eventId), groupsByEvent);
        List<DeptDO> assignerDepts =
                assignerId != null ? deptsByUser.getOrDefault(assignerId, List.of()) : List.of();
        List<DeptDO> assigneeDepts =
                finalAssigneeId != null
                        ? deptsByUser.getOrDefault(finalAssigneeId, List.of())
                        : List.of();

        return toTaskRespVO(task, assigner, assignerDepts, finalAssignee, assigneeDepts);
    }

    @Override
    @Transactional
    @Auditable(operation = "Delete Task", type = AuditType.DATA_CHANGE,
               targetType = "Task", targetId = "#taskId")
    public void deleteTask(Long eventId, Long taskId) {
        EventRespDTO event = eventRpcService.getEvent(eventId);
        if (event == null) {
            throw exception(EVENT_NOT_FOUND);
        }

        TaskDO task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getEventId(), eventId)) {
            throw exception(TASK_NOT_FOUND);
        }
        taskActionFactory.getStrategy(TaskActionEnum.DELETE).execute(task, null);
    }

    @Override
    @DS("slave")
    @Transactional(readOnly = true)
    public TaskRespVO getTask(Long eventId, Long taskId) {
        EventRespDTO event = eventRpcService.getEvent(eventId);
        if (event == null) {
            throw exception(EVENT_NOT_FOUND);
        }

        TaskDO task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getEventId(), eventId)) {
            throw exception(TASK_NOT_FOUND);
        }

        Long assignerId = event.getOrganizerId();
        Long assigneeId = task.getUserId();

        Set<Long> userIds = new LinkedHashSet<>();
        if (assignerId != null) {
            userIds.add(assignerId);
        }
        if (assigneeId != null) {
            userIds.add(assigneeId);
        }

        Map<Long, UserDO> usersById = loadUsers(userIds);
        Map<Long, List<GroupDTO>> groupsByEvent = preloadGroups(List.of(eventId));
        Map<Long, List<DeptDO>> deptsByUser =
                userIds.isEmpty()
                        ? Map.of()
                        : fetchUserDeptsByEvents(userIds, List.of(eventId), groupsByEvent);

        UserDO assigner = assignerId != null ? usersById.get(assignerId) : null;
        UserDO assignee = assigneeId != null ? usersById.get(assigneeId) : null;

        List<DeptDO> assignerDepts =
                assignerId != null ? deptsByUser.getOrDefault(assignerId, List.of()) : List.of();
        List<DeptDO> assigneeDepts =
                assigneeId != null ? deptsByUser.getOrDefault(assigneeId, List.of()) : List.of();

        return toTaskRespVO(task, assigner, assignerDepts, assignee, assigneeDepts);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskRespVO> listTasksByEvent(Long eventId) {
        EventRespDTO event = eventRpcService.getEvent(eventId);
        if (event == null) {
            throw exception(EVENT_NOT_FOUND);
        }

        List<TaskDO> tasks =
                taskMapper.selectList(
                        Wrappers.<TaskDO>lambdaQuery().eq(TaskDO::getEventId, eventId));

        if (tasks.isEmpty()) {
            return List.of();
        }

        List<Long> userIds =
                tasks.stream().map(TaskDO::getUserId).filter(Objects::nonNull).distinct().toList();

        Set<Long> allUserIds = new LinkedHashSet<>(userIds);
        Long assignerId = event.getOrganizerId();
        if (assignerId != null) {
            allUserIds.add(assignerId);
        }

        Map<Long, UserDO> usersById = loadUsers(allUserIds);
        Map<Long, List<GroupDTO>> groupsByEvent = preloadGroups(List.of(eventId));
        Map<Long, List<DeptDO>> deptsByUser =
                allUserIds.isEmpty()
                        ? Map.of()
                        : fetchUserDeptsByEvents(allUserIds, List.of(eventId), groupsByEvent);

        UserDO assigner = assignerId != null ? usersById.get(assignerId) : null;
        List<DeptDO> assignerDepts =
                assignerId != null ? deptsByUser.getOrDefault(assignerId, List.of()) : List.of();

        return tasks.stream()
                .map(
                        task -> {
                            Long userId = task.getUserId();
                            UserDO assignee = userId != null ? usersById.get(userId) : null;
                            List<DeptDO> assigneeDepts =
                                    userId != null
                                            ? deptsByUser.getOrDefault(userId, List.of())
                                            : List.of();
                            return toTaskRespVO(
                                    task, assigner, assignerDepts, assignee, assigneeDepts);
                        })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskRespVO> listTasksByMember(Long memberId) {
        Map<Long, UserDO> memberMap = loadUsers(Set.of(memberId));
        UserDO member = memberMap.get(memberId);
        if (member == null) {
            throw exception(USER_NOT_FOUND);
        }

        List<TaskDO> tasks =
                taskMapper.selectList(
                        Wrappers.<TaskDO>lambdaQuery().eq(TaskDO::getUserId, memberId));

        if (tasks.isEmpty()) {
            return List.of();
        }

        List<Long> eventIds =
                tasks.stream().map(TaskDO::getEventId).filter(Objects::nonNull).distinct().toList();

        Map<Long, EventRespDTO> eventsById = fetchEventsByIds(eventIds);

        Set<Long> userIdsToLoad = new LinkedHashSet<>();
        userIdsToLoad.add(memberId);
        for (EventRespDTO event : eventsById.values()) {
            if (event != null && event.getOrganizerId() != null) {
                userIdsToLoad.add(event.getOrganizerId());
            }
        }

        Map<Long, UserDO> usersById = loadUsers(userIdsToLoad);
        Map<Long, List<GroupDTO>> groupsByEvent = preloadGroups(eventIds);
        Map<Long, List<DeptDO>> deptsByUser =
                fetchUserDeptsByEvents(userIdsToLoad, eventIds, groupsByEvent);

        return tasks.stream()
                .map(
                        task -> {
                            Long eventId = task.getEventId();
                            EventRespDTO event = eventId != null ? eventsById.get(eventId) : null;
                            Long assignerId = event != null ? event.getOrganizerId() : null;
                            UserDO assigner = assignerId != null ? usersById.get(assignerId) : null;
                            List<DeptDO> assignerDepts =
                                    assignerId != null
                                            ? filterDeptsByEvent(
                                                    deptsByUser.getOrDefault(assignerId, List.of()),
                                                    eventId)
                                            : List.of();
                            List<DeptDO> assigneeDepts =
                                    filterDeptsByEvent(
                                            deptsByUser.getOrDefault(memberId, List.of()), eventId);
                            return toTaskRespVO(
                                    task, assigner, assignerDepts, member, assigneeDepts);
                        })
                .toList();
    }

    private List<TasksRespVO> listDashboardTasksByMember(
            UserDO member,
            List<TaskDO> tasks,
            Map<Long, List<GroupDTO>> groupsByEvent,
            Map<Long, EventRespDTO> eventsById) {
        if (tasks.isEmpty()) {
            return List.of();
        }

        List<Long> eventIds =
                tasks.stream().map(TaskDO::getEventId).filter(Objects::nonNull).distinct().toList();

        Map<Long, List<DeptDO>> memberDeptsByEvent =
                fetchUserDeptsByEvents(List.of(member.getId()), eventIds, groupsByEvent);

        return tasks.stream()
                .map(
                        task -> {
                            TasksRespVO respVO = TaskConvert.INSTANCE.toTasksRespVO(task);
                            EventRespDTO event =
                                    task.getEventId() != null
                                            ? eventsById.get(task.getEventId())
                                            : null;
                            respVO.setEvent(toTasksEvent(event));
                            List<DeptDO> depts =
                                    filterDeptsByEvent(
                                            memberDeptsByEvent.getOrDefault(
                                                    member.getId(), List.of()),
                                            task.getEventId());
                            respVO.setAssignedUser(toDashboardAssignedUser(member, depts));
                            return respVO;
                        })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskDashboardRespVO getByMemberId(Long memberId) {
        Map<Long, UserDO> members = loadUsers(Set.of(memberId));
        UserDO member = members.get(memberId);
        if (member == null) {
            throw exception(USER_NOT_FOUND);
        }

        List<TaskDO> memberTasks =
                taskMapper.selectList(
                        Wrappers.<TaskDO>lambdaQuery().eq(TaskDO::getUserId, member.getId()));

        Collection<Long> eventIds =
                memberTasks.stream()
                        .map(TaskDO::getEventId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<GroupDTO>> groupsByEvent = preloadGroups(eventIds);
        Map<Long, EventRespDTO> eventsById = fetchEventsByIds(eventIds);

        TaskDashboardRespVO dashboard = new TaskDashboardRespVO();
        dashboard.setMember(toMemberVO(member));
        dashboard.setGroups(resolveMemberGroups(member, memberTasks, groupsByEvent, eventsById));
        dashboard.setTasks(
                listDashboardTasksByMember(member, memberTasks, groupsByEvent, eventsById));
        return dashboard;
    }

    private Map<Long, UserDO> loadUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return fetchUsersByIds(userIds);
    }

    private TaskRespVO toTaskRespVO(
            TaskDO task,
            UserDO assigner,
            List<DeptDO> assignerDepts,
            UserDO assignee,
            List<DeptDO> assigneeDepts) {
        TaskRespVO respVO = TaskConvert.INSTANCE.toRespVO(task);
        if (assigner != null) {
            respVO.setAssignerUser(toAssignerUser(assigner, assignerDepts));
        }
        if (assignee != null) {
            respVO.setAssignedUser(toAssignedUser(assignee, assigneeDepts));
        }
        return respVO;
    }

    private TaskRespVO.AssignerUserVO toAssignerUser(UserDO user, List<DeptDO> depts) {
        TaskRespVO.AssignerUserVO assignerVO = new TaskRespVO.AssignerUserVO();
        assignerVO.setId(user.getId());
        assignerVO.setName(user.getUsername());
        assignerVO.setEmail(user.getEmail());
        assignerVO.setPhone(user.getPhone());
        assignerVO.setGroups(
                depts == null ? List.of() : depts.stream().map(this::toAssignerGroupVO).toList());
        return assignerVO;
    }

    private TaskRespVO.AssignedUserVO toAssignedUser(UserDO user, List<DeptDO> depts) {
        TaskRespVO.AssignedUserVO assignedVO = new TaskRespVO.AssignedUserVO();
        assignedVO.setId(user.getId());
        assignedVO.setName(user.getUsername());
        assignedVO.setEmail(user.getEmail());
        assignedVO.setPhone(user.getPhone());
        assignedVO.setGroups(
                depts == null ? List.of() : depts.stream().map(this::toCrudGroupVO).toList());
        return assignedVO;
    }

    private List<DeptDO> filterDeptsByEvent(List<DeptDO> depts, Long eventId) {
        if (depts == null || depts.isEmpty()) {
            return List.of();
        }
        if (eventId == null) {
            return depts;
        }
        return depts.stream()
                .filter(dept -> Objects.equals(dept.getEventId(), eventId))
                .collect(Collectors.toList());
    }

    private TasksRespVO.EventVO toTasksEvent(EventRespDTO event) {
        if (event == null) {
            return null;
        }
        TasksRespVO.EventVO eventVO = new TasksRespVO.EventVO();
        eventVO.setId(event.getId());
        eventVO.setName(event.getName());
        eventVO.setDescription(event.getDescription());
        eventVO.setOrganizerId(event.getOrganizerId());
        eventVO.setLocation(event.getLocation());
        eventVO.setStatus(event.getStatus());
        eventVO.setStartTime(event.getStartTime());
        eventVO.setEndTime(event.getEndTime());
        eventVO.setRemark(event.getRemark());
        return eventVO;
    }

    private TasksRespVO.AssignedUserVO toDashboardAssignedUser(UserDO user, List<DeptDO> depts) {
        TasksRespVO.AssignedUserVO assignedUserVO = new TasksRespVO.AssignedUserVO();
        assignedUserVO.setId(user.getId());
        assignedUserVO.setName(user.getUsername());
        assignedUserVO.setEmail(user.getEmail());
        assignedUserVO.setPhone(user.getPhone());
        assignedUserVO.setGroups(
                depts == null ? List.of() : depts.stream().map(this::toDashboardGroupVO).toList());
        return assignedUserVO;
    }

    private Map<Long, EventRespDTO> fetchEventsByIds(Collection<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, EventRespDTO> events = new LinkedHashMap<>();
        for (Long id : eventIds) {
            if (id == null || events.containsKey(id)) {
                continue;
            }
            EventRespDTO event = eventRpcService.getEvent(id);
            if (event != null) {
                events.put(id, event);
            }
        }
        return events;
    }

    private Map<Long, UserDO> fetchUsersByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserInfoDTO> dtoMap = userRpcService.getUsers(userIds);
        if (dtoMap == null || dtoMap.isEmpty()) {
            return Map.of();
        }
        return dtoMap.values().stream()
                .map(this::toUser)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserDO::getId, Function.identity()));
    }

    private Map<Long, List<DeptDO>> fetchUserDeptsByEvent(Collection<Long> userIds, Long eventId) {
        if (eventId == null) {
            return Map.of();
        }
        Map<Long, List<GroupDTO>> groupsByEvent = preloadGroups(List.of(eventId));
        return fetchUserDeptsByEvents(userIds, List.of(eventId), groupsByEvent);
    }

    private Map<Long, List<DeptDO>> fetchUserDeptsByEvents(
            Collection<Long> userIds, Collection<Long> eventIds) {
        return fetchUserDeptsByEvents(userIds, eventIds, null);
    }

    private Map<Long, List<DeptDO>> fetchUserDeptsByEvents(
            Collection<Long> userIds,
            Collection<Long> eventIds,
            Map<Long, List<GroupDTO>> preloadedGroups) {
        if (userIds == null || userIds.isEmpty() || eventIds == null || eventIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<GroupDTO>> groupsByEvent = preloadedGroups;
        if (groupsByEvent == null) {
            List<Long> distinctEventIds =
                    eventIds.stream().filter(Objects::nonNull).distinct().toList();
            if (distinctEventIds.isEmpty()) {
                return Map.of();
            }
            groupsByEvent = groupRpcService.getGroupsByEventIds(distinctEventIds);
        }
        if (groupsByEvent == null || groupsByEvent.isEmpty()) {
            return Map.of();
        }

        Set<Long> userIdSet =
                userIds.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (userIdSet.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<DeptDO>> result = new LinkedHashMap<>();
        Map<Long, Set<Long>> seenGroupIds = new LinkedHashMap<>();

        for (List<GroupDTO> groups : groupsByEvent.values()) {
            if (groups == null || groups.isEmpty()) {
                continue;
            }
            for (GroupDTO group : groups) {
                DeptDO dept = toDept(group);
                if (dept == null || dept.getId() == null) {
                    continue;
                }
                Set<Long> memberIds = extractGroupMemberIds(group);
                for (Long userId : memberIds) {
                    if (!userIdSet.contains(userId)) {
                        continue;
                    }
                    Set<Long> seenIds =
                            seenGroupIds.computeIfAbsent(userId, ignored -> new LinkedHashSet<>());
                    if (seenIds.add(dept.getId())) {
                        result.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(dept);
                    }
                }
            }
        }

        return result;
    }

    private Map<Long, List<GroupDTO>> preloadGroups(Collection<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctEventIds =
                eventIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctEventIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<GroupDTO>> groups = groupRpcService.getGroupsByEventIds(distinctEventIds);
        return groups == null ? Map.of() : groups;
    }

    private Set<Long> extractGroupMemberIds(GroupDTO group) {
        Set<Long> memberIds = new LinkedHashSet<>();
        if (group == null) {
            return memberIds;
        }
        if (group.getLeadUserId() != null) {
            memberIds.add(group.getLeadUserId());
        }
        if (group.getMembers() != null) {
            for (GroupMemberDTO member : group.getMembers()) {
                if (member != null && member.getUserId() != null) {
                    memberIds.add(member.getUserId());
                }
            }
        }
        return memberIds;
    }

    private DeptDO toDept(GroupDTO group) {
        if (group == null) {
            return null;
        }
        DeptDO dept = new DeptDO();
        dept.setId(group.getId());
        dept.setName(group.getName());
        dept.setSort(group.getSort());
        dept.setLeadUserId(group.getLeadUserId());
        dept.setRemark(group.getRemark());
        dept.setStatus(group.getStatus());
        dept.setEventId(group.getEventId());
        return dept;
    }

    private UserDO toUser(UserInfoDTO dto) {
        if (dto == null) {
            return null;
        }
        UserDO user = new UserDO();
        user.setId(dto.getId());
        user.setUsername(dto.getUsername());
        user.setStatus(dto.getStatus());
        user.setTenantId(dto.getTenantId());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setCreateTime(dto.getCreateTime());
        user.setUpdateTime(dto.getUpdateTime());
        return user;
    }

    private TaskRespVO.AssignedUserVO.GroupVO toCrudGroupVO(DeptDO dept) {
        TaskRespVO.AssignedUserVO.GroupVO groupVO = new TaskRespVO.AssignedUserVO.GroupVO();
        groupVO.setId(dept.getId());
        groupVO.setName(dept.getName());
        return groupVO;
    }

    private TaskRespVO.AssignerUserVO.GroupVO toAssignerGroupVO(DeptDO dept) {
        TaskRespVO.AssignerUserVO.GroupVO groupVO = new TaskRespVO.AssignerUserVO.GroupVO();
        groupVO.setId(dept.getId());
        groupVO.setName(dept.getName());
        return groupVO;
    }

    private TasksRespVO.AssignedUserVO.GroupVO toDashboardGroupVO(DeptDO dept) {
        TasksRespVO.AssignedUserVO.GroupVO groupVO = new TasksRespVO.AssignedUserVO.GroupVO();
        groupVO.setId(dept.getId());
        groupVO.setName(dept.getName());
        groupVO.setEventId(dept.getEventId());
        groupVO.setLeadUserId(dept.getLeadUserId());
        groupVO.setRemark(dept.getRemark());
        return groupVO;
    }

    private TaskDashboardRespVO.MemberVO toMemberVO(UserDO member) {
        TaskDashboardRespVO.MemberVO memberVO = new TaskDashboardRespVO.MemberVO();
        memberVO.setId(member.getId());
        memberVO.setUsername(member.getUsername());
        memberVO.setEmail(member.getEmail());
        memberVO.setPhone(member.getPhone());
        memberVO.setStatus(member.getStatus());
        memberVO.setCreateTime(member.getCreateTime());
        memberVO.setUpdateTime(member.getUpdateTime());
        return memberVO;
    }

    private List<TaskDashboardRespVO.GroupVO> resolveMemberGroups(
            UserDO member,
            List<TaskDO> memberTasks,
            Map<Long, List<GroupDTO>> groupsByEvent,
            Map<Long, EventRespDTO> eventsById) {
        if (member == null || member.getId() == null) {
            return List.of();
        }

        Collection<Long> eventIds =
                memberTasks.stream()
                        .map(TaskDO::getEventId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, List<DeptDO>> deptsByUser =
                fetchUserDeptsByEvents(List.of(member.getId()), eventIds, groupsByEvent);
        List<DeptDO> depts = deptsByUser.getOrDefault(member.getId(), List.of());

        return depts.stream()
                .map(
                        dept -> {
                            TaskDashboardRespVO.GroupVO groupVO = new TaskDashboardRespVO.GroupVO();
                            groupVO.setId(dept.getId());
                            groupVO.setName(dept.getName());
                            groupVO.setSort(dept.getSort());
                            groupVO.setLeadUserId(dept.getLeadUserId());
                            groupVO.setRemark(dept.getRemark());
                            groupVO.setStatus(dept.getStatus());
                            groupVO.setEvent(toGroupEvent(eventsById.get(dept.getEventId())));
                            return groupVO;
                        })
                .collect(Collectors.toList());
    }

    private TaskDashboardRespVO.GroupVO.EventVO toGroupEvent(EventRespDTO event) {
        if (event == null) {
            return null;
        }
        TaskDashboardRespVO.GroupVO.EventVO eventVO = new TaskDashboardRespVO.GroupVO.EventVO();
        eventVO.setId(event.getId());
        eventVO.setName(event.getName());
        eventVO.setDescription(event.getDescription());
        eventVO.setLocation(event.getLocation());
        eventVO.setStatus(event.getStatus());
        eventVO.setStartTime(event.getStartTime());
        eventVO.setEndTime(event.getEndTime());
        eventVO.setRemark(event.getRemark());
        return eventVO;
    }
}
