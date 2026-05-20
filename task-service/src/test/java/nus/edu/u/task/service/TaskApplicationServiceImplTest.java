package nus.edu.u.task.service;

import static nus.edu.u.common.enums.ErrorCodeConstants.EVENT_NOT_FOUND;
import static nus.edu.u.common.enums.ErrorCodeConstants.TASK_ASSIGNEE_NOT_FOUND;
import static nus.edu.u.common.enums.ErrorCodeConstants.TASK_ASSIGNEE_TENANT_MISMATCH;
import static nus.edu.u.common.enums.ErrorCodeConstants.TASK_NOT_FOUND;
import static nus.edu.u.common.enums.ErrorCodeConstants.USER_NOT_FOUND;
import static nus.edu.u.common.enums.ErrorCodeConstants.WRONG_TASK_ACTION_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import nus.edu.u.common.exception.ServiceException;
import nus.edu.u.shared.rpc.events.EventRespDTO;
import nus.edu.u.shared.rpc.events.EventRpcService;
import nus.edu.u.shared.rpc.group.GroupDTO;
import nus.edu.u.shared.rpc.group.GroupMemberDTO;
import nus.edu.u.shared.rpc.group.GroupRpcService;
import nus.edu.u.shared.rpc.user.UserInfoDTO;
import nus.edu.u.shared.rpc.user.UserRpcService;
import nus.edu.u.task.action.TaskActionFactory;
import nus.edu.u.task.action.TaskStrategy;
import nus.edu.u.task.domain.dataobject.task.TaskDO;
import nus.edu.u.task.domain.dto.TaskActionDTO;
import nus.edu.u.task.domain.vo.task.TaskCreateReqVO;
import nus.edu.u.task.domain.vo.task.TaskDashboardRespVO;
import nus.edu.u.task.domain.vo.task.TaskRespVO;
import nus.edu.u.task.domain.vo.task.TaskUpdateReqVO;
import nus.edu.u.task.domain.vo.task.TasksRespVO;
import nus.edu.u.task.enums.TaskActionEnum;
import nus.edu.u.task.mapper.TaskMapper;
import nus.edu.u.task.publisher.TaskNotificationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TaskApplicationServiceImplTest {

    @Mock private TaskMapper taskMapper;
    @Mock private TaskActionFactory taskActionFactory;
    @Mock private EventRpcService eventRpcService;
    @Mock private UserRpcService userRpcService;
    @Mock private GroupRpcService groupRpcService;
    @Mock private TaskNotificationPublisher taskNotificationPublisher;

    @InjectMocks private TaskApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "eventRpcService", eventRpcService);
        ReflectionTestUtils.setField(service, "userRpcService", userRpcService);
        ReflectionTestUtils.setField(service, "groupRpcService", groupRpcService);
    }

    @Test
    void createTask_successfullyBuildsResponseWithGroups() {
        long eventId = 10L;
        long organizerId = 100L;
        long assigneeId = 200L;
        LocalDateTime eventStart = LocalDateTime.of(2025, 1, 1, 9, 0);
        LocalDateTime eventEnd = eventStart.plusDays(1);

        EventRespDTO event = event(eventId, organizerId, "Hackathon", eventStart, eventEnd);
        Map<Long, EventRespDTO> events = Map.of(eventId, event);
        stubEvents(events);

        UserInfoDTO organizer = user(organizerId, "Organizer", 9L);
        UserInfoDTO assignee = user(assigneeId, "Assignee", 9L);
        stubUsers(Map.of(organizerId, organizer, assigneeId, assignee));

        GroupDTO group =
                GroupDTO.builder()
                        .id(310L)
                        .eventId(eventId)
                        .name("Ops")
                        .leadUserId(organizerId)
                        .members(List.of(GroupMemberDTO.builder().userId(assigneeId).build()))
                        .remark("Main ops group")
                        .status(1)
                        .build();
        stubGroups(Map.of(eventId, List.of(group)));

        AtomicReference<TaskActionDTO> capturedAction = new AtomicReference<>();
        TaskStrategy createStrategy =
                new TaskStrategy() {
                    @Override
                    public TaskActionEnum getType() {
                        return TaskActionEnum.CREATE;
                    }

                    @Override
                    public void execute(TaskDO task, TaskActionDTO actionDTO, Object... params) {
                        capturedAction.set(actionDTO);
                        task.setId(501L);
                        task.setStatus(1);
                    }
                };
        when(taskActionFactory.getStrategy(TaskActionEnum.CREATE)).thenReturn(createStrategy);

        TaskCreateReqVO request = new TaskCreateReqVO();
        request.setName("Prepare venue");
        request.setDescription("Arrange chairs and tables");
        request.setTargetUserId(assigneeId);
        request.setStartTime(eventStart.plusHours(1));
        request.setEndTime(eventEnd.minusHours(2));

        TaskRespVO response = service.createTask(eventId, request);

        assertThat(response.getId()).isEqualTo(501L);
        assertThat(response.getName()).isEqualTo(request.getName());
        assertThat(response.getEventId()).isEqualTo(eventId);

        TaskRespVO.AssignerUserVO assigner = response.getAssignerUser();
        assertThat(assigner.getId()).isEqualTo(organizerId);
        assertThat(assigner.getGroups())
                .extracting(TaskRespVO.AssignerUserVO.GroupVO::getId)
                .containsExactly(310L);

        TaskRespVO.AssignedUserVO assigned = response.getAssignedUser();
        assertThat(assigned.getId()).isEqualTo(assigneeId);
        assertThat(assigned.getGroups())
                .extracting(TaskRespVO.AssignedUserVO.GroupVO::getId)
                .containsExactly(310L);

        assertThat(capturedAction.get()).isNotNull();
        assertThat(capturedAction.get().getTargetUserId()).isEqualTo(assigneeId);
        assertThat(capturedAction.get().getEventStartTime()).isEqualTo(eventStart);
        assertThat(capturedAction.get().getStartTime()).isEqualTo(request.getStartTime());
    }

    @Test
    void createTask_eventMissing_throwsServiceException() {
        when(eventRpcService.getEvent(anyLong())).thenReturn(null);

        TaskCreateReqVO request = new TaskCreateReqVO();
        request.setTargetUserId(1L);
        request.setName("noop");

        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.createTask(99L, request));
        assertThat(exception.getCode()).isEqualTo(EVENT_NOT_FOUND.getCode());
    }

    @Test
    void createTask_assigneeMissing_throwsServiceException() {
        long eventId = 22L;
        long organizerId = 90L;
        stubEvents(
                Map.of(
                        eventId,
                        event(
                                eventId,
                                organizerId,
                                "Conference",
                                LocalDateTime.now(),
                                LocalDateTime.now().plusHours(5))));

        stubUsers(Map.of(organizerId, user(organizerId, "Org", 3L)));

        TaskCreateReqVO req = new TaskCreateReqVO();
        req.setName("Assign");
        req.setTargetUserId(3000L);

        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.createTask(eventId, req));
        assertThat(exception.getCode()).isEqualTo(TASK_ASSIGNEE_NOT_FOUND.getCode());
    }

    @Test
    void createTask_tenantMismatch_throwsServiceException() {
        long eventId = 33L;
        long organizerId = 10L;
        long assigneeId = 20L;
        stubEvents(
                Map.of(
                        eventId,
                        event(
                                eventId,
                                organizerId,
                                "Seminar",
                                LocalDateTime.now(),
                                LocalDateTime.now().plusHours(3))));

        stubUsers(
                Map.of(
                        organizerId,
                        user(organizerId, "Org", 1L),
                        assigneeId,
                        user(assigneeId, "Member", 2L)));

        TaskCreateReqVO req = new TaskCreateReqVO();
        req.setName("Task");
        req.setTargetUserId(assigneeId);

        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.createTask(eventId, req));
        assertThat(exception.getCode()).isEqualTo(TASK_ASSIGNEE_TENANT_MISMATCH.getCode());
    }

    @Test
    void updateTask_updatesFieldsAndResolvesGroups() {
        long eventId = 44L;
        long taskId = 5L;
        long organizerId = 600L;
        long originalAssignee = 700L;
        long newAssignee = 701L;

        LocalDateTime eventStart = LocalDateTime.of(2025, 2, 10, 10, 0);
        LocalDateTime eventEnd = eventStart.plusDays(2);
        EventRespDTO event = event(eventId, organizerId, "Expo", eventStart, eventEnd);
        stubEvents(Map.of(eventId, event));

        TaskDO dbTask =
                TaskDO.builder()
                        .id(taskId)
                        .eventId(eventId)
                        .userId(originalAssignee)
                        .name("Old")
                        .description("desc")
                        .build();
        when(taskMapper.selectById(taskId)).thenReturn(dbTask);

        stubUsers(
                Map.of(
                        organizerId,
                        user(organizerId, "Organizer", 4L),
                        originalAssignee,
                        user(originalAssignee, "Original", 4L),
                        newAssignee,
                        user(newAssignee, "Target", 4L)));

        GroupDTO group =
                GroupDTO.builder()
                        .id(901L)
                        .eventId(eventId)
                        .name("Stage crew")
                        .leadUserId(organizerId)
                        .members(
                                Arrays.asList(
                                        GroupMemberDTO.builder().userId(originalAssignee).build(),
                                        GroupMemberDTO.builder().userId(newAssignee).build()))
                        .status(1)
                        .build();
        stubGroups(Map.of(eventId, List.of(group)));

        AtomicReference<TaskActionDTO> captured = new AtomicReference<>();
        TaskStrategy assignStrategy =
                new TaskStrategy() {
                    @Override
                    public TaskActionEnum getType() {
                        return TaskActionEnum.ASSIGN;
                    }

                    @Override
                    public void execute(TaskDO task, TaskActionDTO actionDTO, Object... params) {
                        captured.set(actionDTO);
                        task.setName(actionDTO.getName());
                        task.setDescription(actionDTO.getDescription());
                        task.setUserId(actionDTO.getTargetUserId());
                    }
                };
        when(taskActionFactory.getStrategy(TaskActionEnum.ASSIGN)).thenReturn(assignStrategy);

        TaskUpdateReqVO req = new TaskUpdateReqVO();
        req.setName("Updated name");
        req.setDescription("Updated desc");
        req.setTargetUserId(newAssignee);
        req.setStartTime(eventStart.plusHours(2));
        req.setEndTime(eventEnd.minusHours(3));
        req.setType(TaskActionEnum.ASSIGN.getCode());

        TaskRespVO response = service.updateTask(eventId, taskId, req, req.getType());

        assertThat(response.getName()).isEqualTo("Updated name");
        assertThat(response.getAssignedUser().getId()).isEqualTo(newAssignee);
        assertThat(response.getAssignedUser().getGroups())
                .extracting(TaskRespVO.AssignedUserVO.GroupVO::getId)
                .containsExactly(901L);
        assertThat(response.getAssignerUser().getId()).isEqualTo(organizerId);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getTargetUserId()).isEqualTo(newAssignee);
        assertThat(captured.get().getStartTime()).isEqualTo(req.getStartTime());
    }

    @Test
    void updateTask_whenInvalidActionType_throwsServiceException() {
        long eventId = 88L;
        long taskId = 9L;
        long organizerId = 801L;
        stubEvents(
                Map.of(
                        eventId,
                        event(
                                eventId,
                                organizerId,
                                "Summit",
                                LocalDateTime.now(),
                                LocalDateTime.now().plusHours(1))));
        when(taskMapper.selectById(taskId))
                .thenReturn(TaskDO.builder().id(taskId).eventId(eventId).build());
        stubUsers(Map.of(organizerId, user(organizerId, "Org", 5L)));

        TaskUpdateReqVO req = new TaskUpdateReqVO();
        req.setType(TaskActionEnum.CREATE.getCode());

        ServiceException exception =
                assertThrows(
                        ServiceException.class,
                        () -> service.updateTask(eventId, taskId, req, req.getType()));
        assertThat(exception.getCode()).isEqualTo(WRONG_TASK_ACTION_TYPE.getCode());
    }

    @Test
    void updateTask_whenTaskMissing_throwsServiceException() {
        long eventId = 120L;
        long organizerId = 77L;
        stubEvents(
                Map.of(
                        eventId,
                        event(
                                eventId,
                                organizerId,
                                "Workshop",
                                LocalDateTime.now(),
                                LocalDateTime.now().plusHours(2))));
        when(taskMapper.selectById(5L)).thenReturn(null);

        TaskUpdateReqVO req = new TaskUpdateReqVO();
        req.setType(TaskActionEnum.ASSIGN.getCode());

        ServiceException exception =
                assertThrows(
                        ServiceException.class,
                        () -> service.updateTask(eventId, 5L, req, req.getType()));
        assertThat(exception.getCode()).isEqualTo(TASK_NOT_FOUND.getCode());
    }

    @Test
    void updateTask_whenEventMissing_throwsServiceException() {
        long eventId = 5000L;
        stubEvents(new LinkedHashMap<>());

        TaskUpdateReqVO req = new TaskUpdateReqVO();
        req.setType(TaskActionEnum.UPDATE.getCode());

        ServiceException exception =
                assertThrows(
                        ServiceException.class,
                        () -> service.updateTask(eventId, 1L, req, req.getType()));
        assertThat(exception.getCode()).isEqualTo(EVENT_NOT_FOUND.getCode());
    }

    @Test
    void updateTask_withoutTargetUser_usesExistingAssignment() {
        long eventId = 77L;
        long taskId = 88L;
        long organizerId = 501L;
        long existingAssignee = 601L;

        EventRespDTO event =
                event(
                        eventId,
                        organizerId,
                        "Sprint",
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(1));
        stubEvents(Map.of(eventId, event));

        TaskDO dbTask =
                TaskDO.builder()
                        .id(taskId)
                        .eventId(eventId)
                        .userId(existingAssignee)
                        .name("Backlog grooming")
                        .build();
        when(taskMapper.selectById(taskId)).thenReturn(dbTask);

        stubUsers(Map.of(existingAssignee, user(existingAssignee, "Member", null)));

        TaskStrategy updateStrategy =
                new TaskStrategy() {
                    @Override
                    public TaskActionEnum getType() {
                        return TaskActionEnum.UPDATE;
                    }

                    @Override
                    public void execute(TaskDO task, TaskActionDTO actionDTO, Object... params) {
                        task.setDescription("Updated");
                    }
                };
        when(taskActionFactory.getStrategy(TaskActionEnum.UPDATE)).thenReturn(updateStrategy);

        TaskUpdateReqVO req = new TaskUpdateReqVO();
        req.setType(TaskActionEnum.UPDATE.getCode());
        req.setDescription("Updated");

        TaskRespVO response = service.updateTask(eventId, taskId, req, req.getType());

        assertThat(response.getAssignedUser().getId()).isEqualTo(existingAssignee);
        assertThat(response.getAssignerUser()).isNull();
    }

    @Test
    void deleteTask_invokesStrategyAfterValidations() {
        long eventId = 201L;
        long taskId = 301L;
        long organizerId = 901L;
        stubEvents(
                Map.of(
                        eventId,
                        event(
                                eventId,
                                organizerId,
                                "Cleanup",
                                LocalDateTime.now(),
                                LocalDateTime.now().plusHours(4))));

        TaskDO task = TaskDO.builder().id(taskId).eventId(eventId).userId(organizerId).build();
        when(taskMapper.selectById(taskId)).thenReturn(task);

        AtomicReference<Boolean> executed = new AtomicReference<>(false);
        TaskStrategy deleteStrategy =
                new TaskStrategy() {
                    @Override
                    public TaskActionEnum getType() {
                        return TaskActionEnum.DELETE;
                    }

                    @Override
                    public void execute(TaskDO task, TaskActionDTO actionDTO, Object... params) {
                        executed.set(true);
                        assertThat(actionDTO).isNull();
                    }
                };
        when(taskActionFactory.getStrategy(TaskActionEnum.DELETE)).thenReturn(deleteStrategy);

        service.deleteTask(eventId, taskId);
        assertThat(executed.get()).isTrue();
    }

    @Test
    void deleteTask_whenTaskBelongsToDifferentEvent_throwsException() {
        long eventId = 77L;
        long organizerId = 11L;
        stubEvents(
                Map.of(
                        eventId,
                        event(
                                eventId,
                                organizerId,
                                "Meetup",
                                LocalDateTime.now(),
                                LocalDateTime.now().plusHours(2))));
        when(taskMapper.selectById(9L)).thenReturn(TaskDO.builder().id(9L).eventId(999L).build());

        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.deleteTask(eventId, 9L));
        assertThat(exception.getCode()).isEqualTo(TASK_NOT_FOUND.getCode());
    }

    @Test
    void deleteTask_whenEventMissing_throwsServiceException() {
        stubEvents(new LinkedHashMap<>());

        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.deleteTask(123L, 1L));
        assertThat(exception.getCode()).isEqualTo(EVENT_NOT_FOUND.getCode());
    }

    @Test
    void getTask_returnsEnrichedResponse() {
        long eventId = 512L;
        long taskId = 9001L;
        long organizerId = 700L;
        long assigneeId = 701L;

        EventRespDTO event =
                event(
                        eventId,
                        organizerId,
                        "Planning",
                        LocalDateTime.now(),
                        LocalDateTime.now().plusHours(5));
        stubEvents(Map.of(eventId, event));

        TaskDO dbTask =
                TaskDO.builder()
                        .id(taskId)
                        .eventId(eventId)
                        .userId(assigneeId)
                        .name("Draft agenda")
                        .description("Outline topics")
                        .status(1)
                        .build();
        when(taskMapper.selectById(taskId)).thenReturn(dbTask);

        stubUsers(
                Map.of(
                        organizerId,
                        user(organizerId, "Lead", 15L),
                        assigneeId,
                        user(assigneeId, "Member", 15L)));

        GroupDTO group =
                GroupDTO.builder()
                        .id(1400L)
                        .eventId(eventId)
                        .name("Org team")
                        .leadUserId(organizerId)
                        .members(List.of(GroupMemberDTO.builder().userId(assigneeId).build()))
                        .status(1)
                        .build();
        stubGroups(Map.of(eventId, List.of(group)));

        TaskRespVO response = service.getTask(eventId, taskId);

        assertThat(response.getId()).isEqualTo(taskId);
        assertThat(response.getEventId()).isEqualTo(eventId);
        assertThat(response.getAssignerUser().getGroups()).isNotEmpty();
        assertThat(response.getAssignedUser().getGroups()).isNotEmpty();
    }

    @Test
    void getTask_whenTaskMissing_throwsServiceException() {
        long eventId = 811L;
        long organizerId = 3000L;
        stubEvents(
                Map.of(
                        eventId,
                        event(
                                eventId,
                                organizerId,
                                "Briefing",
                                LocalDateTime.now(),
                                LocalDateTime.now().plusHours(2))));
        when(taskMapper.selectById(100L)).thenReturn(null);

        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.getTask(eventId, 100L));
        assertThat(exception.getCode()).isEqualTo(TASK_NOT_FOUND.getCode());
    }

    @Test
    void getTask_whenEventMissing_throwsServiceException() {
        stubEvents(new LinkedHashMap<>());

        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.getTask(999L, 1L));
        assertThat(exception.getCode()).isEqualTo(EVENT_NOT_FOUND.getCode());
    }

    @Test
    void listTasksByEvent_populatesUsersGroupsAndFallbacks() {
        long eventId = 310L;
        long organizerId = 88L;
        stubEvents(
                Map.of(
                        eventId,
                        event(
                                eventId,
                                organizerId,
                                "Townhall",
                                LocalDateTime.now(),
                                LocalDateTime.now().plusHours(3))));

        TaskDO assigned =
                TaskDO.builder()
                        .id(1L)
                        .eventId(eventId)
                        .userId(501L)
                        .name("Arrange Catering")
                        .status(1)
                        .build();
        TaskDO unassigned =
                TaskDO.builder()
                        .id(2L)
                        .eventId(eventId)
                        .userId(null)
                        .name("Book Venue")
                        .status(2)
                        .build();
        when(taskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(assigned, unassigned));

        stubUsers(
                Map.of(organizerId, user(organizerId, "Org", 6L), 501L, user(501L, "User501", 6L)));

        GroupDTO group =
                GroupDTO.builder()
                        .id(710L)
                        .eventId(eventId)
                        .name("Hospitality")
                        .leadUserId(organizerId)
                        .members(List.of(GroupMemberDTO.builder().userId(501L).build()))
                        .status(1)
                        .build();
        stubGroups(Map.of(eventId, List.of(group)));

        List<TaskRespVO> tasks = service.listTasksByEvent(eventId);

        assertThat(tasks).hasSize(2);
        TaskRespVO first = tasks.get(0);
        assertThat(first.getAssignedUser().getId()).isEqualTo(501L);
        assertThat(first.getAssignedUser().getGroups())
                .extracting(TaskRespVO.AssignedUserVO.GroupVO::getName)
                .containsExactly("Hospitality");

        TaskRespVO second = tasks.get(1);
        assertThat(second.getAssignedUser()).isNull();
    }

    @Test
    void listTasksByEvent_whenNoTasks_returnsEmptyList() {
        long eventId = 400L;
        long organizerId = 401L;
        stubEvents(
                Map.of(
                        eventId,
                        event(
                                eventId,
                                organizerId,
                                "Briefing",
                                LocalDateTime.now(),
                                LocalDateTime.now().plusHours(1))));
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<TaskRespVO> tasks = service.listTasksByEvent(eventId);
        assertThat(tasks).isEmpty();
    }

    @Test
    void listTasksByEvent_whenGroupsMissing_returnsUsersWithoutGroupData() {
        long eventId = 612L;
        long organizerId = 710L;
        long assigneeId = 711L;
        EventRespDTO eventDto =
                event(
                        eventId,
                        organizerId,
                        "Briefing",
                        LocalDateTime.now(),
                        LocalDateTime.now().plusHours(1));
        stubEvents(Map.of(eventId, eventDto));

        TaskDO task =
                TaskDO.builder()
                        .id(90L)
                        .eventId(eventId)
                        .userId(assigneeId)
                        .name("Set up stage")
                        .build();
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(task));

        stubUsers(
                Map.of(
                        organizerId,
                        user(organizerId, "Organizer", 8L),
                        assigneeId,
                        user(assigneeId, "Assignee", 8L)));
        when(groupRpcService.getGroupsByEventIds(any())).thenReturn(null);

        List<TaskRespVO> responses = service.listTasksByEvent(eventId);

        assertThat(responses).hasSize(1);
        TaskRespVO resp = responses.get(0);
        assertThat(resp.getAssignerUser().getGroups()).isEmpty();
        assertThat(resp.getAssignedUser().getGroups()).isEmpty();
    }

    @Test
    void listTasksByMember_deduplicatesGroupAssignmentsPerUser() {
        long memberId = 812L;
        long eventId = 913L;
        long organizerId = 1001L;

        TaskDO task =
                TaskDO.builder()
                        .id(11L)
                        .eventId(eventId)
                        .userId(memberId)
                        .name("Manage booth")
                        .build();
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(task));

        stubUsers(
                Map.of(
                        memberId,
                        user(memberId, "Member", 11L),
                        organizerId,
                        user(organizerId, "Organizer", 11L)));
        EventRespDTO eventDto =
                event(
                        eventId,
                        organizerId,
                        "Expo",
                        LocalDateTime.now(),
                        LocalDateTime.now().plusHours(4));
        stubEvents(Map.of(eventId, eventDto));

        GroupDTO first =
                GroupDTO.builder()
                        .id(501L)
                        .eventId(eventId)
                        .name("Volunteers")
                        .leadUserId(organizerId)
                        .members(List.of(GroupMemberDTO.builder().userId(memberId).build()))
                        .status(1)
                        .build();
        GroupDTO duplicate =
                GroupDTO.builder()
                        .id(501L)
                        .eventId(eventId)
                        .name("Volunteers Duplicate")
                        .leadUserId(organizerId)
                        .members(
                                List.of(
                                        GroupMemberDTO.builder().userId(memberId).build(),
                                        GroupMemberDTO.builder().userId(memberId).build()))
                        .status(1)
                        .build();
        stubGroups(Map.of(eventId, List.of(first, duplicate)));

        List<TaskRespVO> responses = service.listTasksByMember(memberId);

        assertThat(responses).hasSize(1);
        TaskRespVO resp = responses.get(0);
        assertThat(resp.getAssignedUser().getGroups()).hasSize(1);
        assertThat(resp.getAssignedUser().getGroups().get(0).getId()).isEqualTo(501L);
    }

    @Test
    void listTasksByMember_enrichesAssignmentsUsingCaches() {
        long memberId = 600L;
        long eventA = 20L;
        long eventB = 21L;
        long organizerA = 901L;
        long organizerB = 902L;

        TaskDO taskA =
                TaskDO.builder().id(1L).eventId(eventA).userId(memberId).name("Task A").build();
        TaskDO taskB =
                TaskDO.builder().id(2L).eventId(eventB).userId(memberId).name("Task B").build();
        when(taskMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(taskA, taskB));

        stubUsers(
                Map.of(
                        memberId,
                        user(memberId, "Member", 10L),
                        organizerA,
                        user(organizerA, "Organizer A", 10L),
                        organizerB,
                        user(organizerB, "Organizer B", 11L)));

        EventRespDTO eventDtoA =
                event(
                        eventA,
                        organizerA,
                        "Event A",
                        LocalDateTime.now(),
                        LocalDateTime.now().plusHours(2));
        EventRespDTO eventDtoB =
                event(
                        eventB,
                        organizerB,
                        "Event B",
                        LocalDateTime.now(),
                        LocalDateTime.now().plusHours(3));
        stubEvents(Map.of(eventA, eventDtoA, eventB, eventDtoB));

        GroupDTO groupA =
                GroupDTO.builder()
                        .id(1001L)
                        .eventId(eventA)
                        .name("Alpha")
                        .members(List.of(GroupMemberDTO.builder().userId(memberId).build()))
                        .leadUserId(organizerA)
                        .status(1)
                        .build();
        GroupDTO groupB =
                GroupDTO.builder()
                        .id(1002L)
                        .eventId(eventB)
                        .name("Beta")
                        .members(List.of(GroupMemberDTO.builder().userId(memberId).build()))
                        .leadUserId(organizerB)
                        .status(1)
                        .build();
        stubGroups(Map.of(eventA, List.of(groupA), eventB, List.of(groupB)));

        List<TaskRespVO> responses = service.listTasksByMember(memberId);

        assertThat(responses).hasSize(2);
        assertThat(responses)
                .extracting(TaskRespVO::getEventId)
                .containsExactlyInAnyOrder(eventA, eventB);
        assertThat(responses.get(0).getAssignedUser().getGroups()).isNotEmpty();
    }

    @Test
    void listTasksByMember_whenUserMissing_throwsServiceException() {
        when(userRpcService.getUsers(any())).thenReturn(null);
        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.listTasksByMember(88L));
        assertThat(exception.getCode()).isEqualTo(USER_NOT_FOUND.getCode());
    }

    @Test
    void getByMemberId_buildsDashboardWithGroupsAndTasks() {
        long memberId = 701L;
        long eventId = 9010L;
        long organizerId = 5000L;

        TaskDO task =
                TaskDO.builder()
                        .id(1L)
                        .eventId(eventId)
                        .userId(memberId)
                        .name("Prepare slides")
                        .build();
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(task));

        stubUsers(
                Map.of(
                        memberId,
                        user(memberId, "Member", 12L),
                        organizerId,
                        user(organizerId, "Organizer", 12L)));

        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusHours(4);
        stubEvents(Map.of(eventId, event(eventId, organizerId, "Showcase", start, end)));

        GroupDTO group =
                GroupDTO.builder()
                        .id(123L)
                        .eventId(eventId)
                        .name("Presentation")
                        .leadUserId(organizerId)
                        .members(List.of(GroupMemberDTO.builder().userId(memberId).build()))
                        .remark("Key presenters")
                        .status(1)
                        .build();
        stubGroups(Map.of(eventId, List.of(group)));

        TaskDashboardRespVO dashboard = service.getByMemberId(memberId);

        assertThat(dashboard.getMember().getId()).isEqualTo(memberId);
        assertThat(dashboard.getGroups()).hasSize(1);
        assertThat(dashboard.getGroups().get(0).getEvent().getName()).isEqualTo("Showcase");
        assertThat(dashboard.getTasks()).hasSize(1);
        TasksRespVO taskSummary = dashboard.getTasks().get(0);
        assertThat(taskSummary.getAssignedUser().getGroups()).hasSize(1);
    }

    @Test
    void getByMemberId_whenNoTasks_returnsEmptyCollections() {
        long memberId = 44L;
        stubUsers(Map.of(memberId, user(memberId, "Solo", 1L)));
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        TaskDashboardRespVO dashboard = service.getByMemberId(memberId);

        assertThat(dashboard.getTasks()).isEmpty();
        assertThat(dashboard.getGroups()).isEmpty();
    }

    @Test
    void getByMemberId_whenMemberMissing_throwsServiceException() {
        when(userRpcService.getUsers(any())).thenReturn(null);
        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.getByMemberId(1234L));
        assertThat(exception.getCode()).isEqualTo(USER_NOT_FOUND.getCode());
    }

    private void stubEvents(Map<Long, EventRespDTO> eventsById) {
        when(eventRpcService.getEvent(any()))
                .thenAnswer(
                        invocation -> {
                            Long id = invocation.getArgument(0);
                            return eventsById.get(id);
                        });
    }

    private void stubUsers(Map<Long, UserInfoDTO> usersById) {
        when(userRpcService.getUsers(any()))
                .thenAnswer(
                        invocation -> {
                            Object argument = invocation.getArgument(0);
                            if (!(argument instanceof Collection<?> ids)) {
                                return Map.of();
                            }
                            Map<Long, UserInfoDTO> result = new LinkedHashMap<>();
                            for (Object rawId : ids) {
                                if (rawId instanceof Long id && usersById.containsKey(id)) {
                                    result.put(id, usersById.get(id));
                                }
                            }
                            return result;
                        });
    }

    private void stubGroups(Map<Long, List<GroupDTO>> groupsByEventId) {
        when(groupRpcService.getGroupsByEventIds(any()))
                .thenAnswer(
                        invocation -> {
                            Object argument = invocation.getArgument(0);
                            if (!(argument instanceof Collection<?> ids)) {
                                return Map.of();
                            }
                            Map<Long, List<GroupDTO>> result = new LinkedHashMap<>();
                            for (Object rawId : ids) {
                                if (rawId instanceof Long id) {
                                    result.put(id, groupsByEventId.getOrDefault(id, List.of()));
                                }
                            }
                            return result;
                        });
    }

    private EventRespDTO event(
            Long id, Long organizerId, String name, LocalDateTime start, LocalDateTime end) {
        EventRespDTO event = new EventRespDTO();
        event.setId(id);
        event.setOrganizerId(organizerId);
        event.setName(name);
        event.setDescription(name + " desc");
        event.setLocation("Hall");
        event.setStatus(1);
        event.setStartTime(start);
        event.setEndTime(end);
        event.setRemark("remark");
        return event;
    }

    private UserInfoDTO user(Long id, String name, Long tenantId) {
        return UserInfoDTO.builder()
                .id(id)
                .username(name)
                .tenantId(tenantId)
                .status(1)
                .email(name.toLowerCase() + "@chronoflow.sg")
                .phone("1234567")
                .build();
    }
}
