package nus.edu.u.event.service;

import static nus.edu.u.common.enums.ErrorCodeConstants.EVENT_DELETE_FAILED;
import static nus.edu.u.common.enums.ErrorCodeConstants.EVENT_NOT_DELETED;
import static nus.edu.u.common.enums.ErrorCodeConstants.EVENT_NOT_FOUND;
import static nus.edu.u.common.enums.ErrorCodeConstants.EVENT_RESTORE_FAILED;
import static nus.edu.u.common.enums.ErrorCodeConstants.TASK_DELETE_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import nus.edu.u.common.enums.EventStatusEnum;
import nus.edu.u.common.exception.ServiceException;
import nus.edu.u.event.convert.EventConvert;
import nus.edu.u.event.domain.dataobject.event.EventDO;
import nus.edu.u.event.domain.dataobject.user.UserGroupDO;
import nus.edu.u.event.domain.dto.event.EventCreateReqVO;
import nus.edu.u.event.domain.dto.event.EventDTO;
import nus.edu.u.event.domain.dto.event.EventGroupRespVO;
import nus.edu.u.event.domain.dto.event.EventRespVO;
import nus.edu.u.event.domain.dto.event.EventUpdateReqVO;
import nus.edu.u.event.domain.dto.event.UpdateEventRespVO;
import nus.edu.u.event.domain.dto.group.GroupRespVO;
import nus.edu.u.event.enums.TaskStatusEnum;
import nus.edu.u.event.mapper.EventMapper;
import nus.edu.u.event.mapper.UserGroupMapper;
import nus.edu.u.event.service.validation.EventValidationHandler;
import nus.edu.u.shared.rpc.group.GroupDTO;
import nus.edu.u.shared.rpc.group.GroupMemberDTO;
import nus.edu.u.shared.rpc.task.TaskDTO;
import nus.edu.u.shared.rpc.task.TaskRpcService;
import nus.edu.u.shared.rpc.user.UserRpcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EventApplicationServiceImplTest {

    @Mock private EventMapper eventMapper;
    @Mock private UserGroupMapper userGroupMapper;
    @Mock private EventConvert eventConvert;
    @Mock private GroupApplicationService groupApplicationService;
    @Mock private UserRpcService userRpcService;
    @Mock private TaskRpcService taskRpcService;

    @InjectMocks private EventApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "userRpcService", userRpcService);
        ReflectionTestUtils.setField(service, "taskRpcService", taskRpcService);
        ReflectionTestUtils.setField(
                service, "validationHandlers", List.<EventValidationHandler>of());
    }

    @Test
    void createEvent_runsValidationsAndSetsDefaultStatus() {
        EventCreateReqVO req =
                new EventCreateReqVO() {
                    {
                        setOrganizerId(88L);
                        setStartTime(LocalDateTime.of(2025, 1, 1, 9, 0));
                        setEndTime(LocalDateTime.of(2025, 1, 1, 12, 0));
                        setParticipantUserIds(List.of(1L, 2L));
                    }
                };

        EventDTO dto =
                EventDTO.builder()
                        .id(101L)
                        .organizerId(req.getOrganizerId())
                        .startTime(req.getStartTime())
                        .endTime(req.getEndTime())
                        .build();
        EventDO eventBeforeInsert =
                EventDO.builder()
                        .id(101L)
                        .userId(req.getOrganizerId())
                        .startTime(req.getStartTime())
                        .endTime(req.getEndTime())
                        .location("hall")
                        .description("desc")
                        .remark("remark")
                        .build();
        EventDO persisted =
                eventBeforeInsert.toBuilder().status(EventStatusEnum.ACTIVE.getCode()).build();
        persisted.setCreateTime(LocalDateTime.of(2024, 12, 31, 10, 0));

        EventRespVO mappedResp = new EventRespVO();
        mappedResp.setId(persisted.getId());

        EventValidationHandler supporting = mock(EventValidationHandler.class);
        EventValidationHandler skipping = mock(EventValidationHandler.class);
        when(supporting.supports(any())).thenReturn(true);
        when(skipping.supports(any())).thenReturn(false);
        ReflectionTestUtils.setField(service, "validationHandlers", List.of(supporting, skipping));

        when(eventConvert.convert(req)).thenReturn(dto);
        when(eventConvert.convert(dto)).thenReturn(eventBeforeInsert);
        when(eventMapper.insert(eventBeforeInsert)).thenReturn(1);
        when(eventMapper.selectById(persisted.getId())).thenReturn(persisted);
        when(eventConvert.DOconvertVO(persisted)).thenReturn(mappedResp);
        when(userGroupMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(groupApplicationService.getGroupsByEventIds(anyCollection()))
                .thenReturn(Collections.emptyMap());
        when(taskRpcService.getTasksByEventIds(anyList())).thenReturn(Collections.emptyMap());

        EventRespVO result = service.createEvent(req);

        assertThat(result).isSameAs(mappedResp);

        ArgumentCaptor<EventDO> insertedCaptor = ArgumentCaptor.forClass(EventDO.class);
        verify(eventMapper).insert(insertedCaptor.capture());
        assertThat(insertedCaptor.getValue().getStatus())
                .isEqualTo(EventStatusEnum.ACTIVE.getCode());

        verify(supporting).validate(any());
        verify(skipping, never()).validate(any());
    }

    @Test
    void getEvent_notFound_throwsServiceException() {
        long id = 999L;
        when(eventMapper.selectById(id)).thenReturn(null);

        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.getEvent(id));

        assertThat(exception.getCode()).isEqualTo(EVENT_NOT_FOUND.getCode());
    }

    @Test
    void getEvent_populatesResponseWhenMapperReturnsNullConversion() {
        long id = 321L;
        EventDO db =
                EventDO.builder()
                        .id(id)
                        .name("Conference")
                        .description("Annual meetup")
                        .userId(44L)
                        .location("Auditorium")
                        .startTime(LocalDateTime.of(2025, 1, 10, 9, 0))
                        .endTime(LocalDateTime.of(2025, 1, 10, 17, 0))
                        .status(EventStatusEnum.ACTIVE.getCode())
                        .remark("note")
                        .build();
        db.setCreateTime(LocalDateTime.of(2024, 12, 1, 12, 0));

        when(eventMapper.selectById(id)).thenReturn(db);
        when(eventConvert.DOconvertVO(db)).thenReturn(null);
        when(userGroupMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(groupApplicationService.getGroupsByEventIds(anyCollection()))
                .thenReturn(Collections.emptyMap());
        when(taskRpcService.getTasksByEventIds(anyList())).thenReturn(Collections.emptyMap());

        EventRespVO resp = service.getEvent(id);

        assertThat(resp.getId()).isEqualTo(id);
        assertThat(resp.getName()).isEqualTo(db.getName());
        assertThat(resp.getDescription()).isEqualTo(db.getDescription());
        assertThat(resp.getOrganizerId()).isEqualTo(db.getUserId());
        assertThat(resp.getStartTime()).isEqualTo(db.getStartTime());
        assertThat(resp.getEndTime()).isEqualTo(db.getEndTime());
        assertThat(resp.getStatus()).isEqualTo(db.getStatus());
    }

    @Test
    void deleteEvent_taskDeletionFailure_throwsServiceException() {
        long eventId = 200L;
        EventDO existing =
                EventDO.builder()
                        .id(eventId)
                        .userId(1L)
                        .name("to delete")
                        .description("desc")
                        .startTime(LocalDateTime.now())
                        .endTime(LocalDateTime.now().plusHours(1))
                        .location("loc")
                        .status(EventStatusEnum.ACTIVE.getCode())
                        .remark("remark")
                        .build();

        when(eventMapper.selectById(eventId)).thenReturn(existing);
        when(eventMapper.deleteById(eventId)).thenReturn(1);
        doThrow(new RuntimeException("rpc error"))
                .when(taskRpcService)
                .deleteTasksByEventId(eventId);

        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.deleteEvent(eventId));

        assertThat(exception.getCode()).isEqualTo(TASK_DELETE_FAILED.getCode());
    }

    @Test
    void updateEvent_patchesEntityAndReturnsAugmentedResponse() {
        long id = 700L;
        EventDO current =
                EventDO.builder()
                        .id(id)
                        .userId(3L)
                        .name("Old")
                        .description("desc")
                        .startTime(LocalDateTime.of(2025, 1, 1, 10, 0))
                        .endTime(LocalDateTime.of(2025, 1, 1, 12, 0))
                        .location("room")
                        .status(EventStatusEnum.ACTIVE.getCode())
                        .remark("remark")
                        .build();
        EventDO updated =
                current.toBuilder()
                        .name("Updated")
                        .description("new desc")
                        .location("hall")
                        .status(EventStatusEnum.NOT_STARTED.getCode())
                        .build();
        updated.setUpdateTime(LocalDateTime.of(2025, 1, 2, 9, 0));

        EventUpdateReqVO req =
                new EventUpdateReqVO() {
                    {
                        setEventName("Updated");
                        setDescription("new desc");
                        setLocation("hall");
                        setStatus(EventStatusEnum.NOT_STARTED.getCode());
                        setParticipantUserIds(List.of(9L, 9L, 10L));
                    }
                };

        UpdateEventRespVO resp = new UpdateEventRespVO();
        when(eventMapper.selectById(id)).thenReturn(current, updated);
        when(eventMapper.updateById(current)).thenReturn(1);
        when(eventConvert.toUpdateResp(updated)).thenReturn(resp);
        when(userGroupMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                UserGroupDO.builder().id(1L).eventId(id).userId(9L).build(),
                                UserGroupDO.builder().id(2L).eventId(id).userId(10L).build()));

        doAnswer(
                        invocation -> {
                            EventDO target = invocation.getArgument(0);
                            EventUpdateReqVO source = invocation.getArgument(1);
                            target.setName(source.getEventName());
                            target.setDescription(source.getDescription());
                            target.setLocation(source.getLocation());
                            target.setStatus(source.getStatus());
                            return null;
                        })
                .when(eventConvert)
                .patch(eq(current), eq(req));

        UpdateEventRespVO result = service.updateEvent(id, req);

        assertThat(result.getParticipantUserIds()).containsExactlyInAnyOrder(9L, 10L);
        assertThat(result.getUpdateTime()).isEqualTo(updated.getUpdateTime());
        verify(eventConvert).patch(eq(current), eq(req));
    }

    @Test
    void updateEvent_notFound_throwsServiceException() {
        when(eventMapper.selectById(1L)).thenReturn(null);

        EventUpdateReqVO req = new EventUpdateReqVO();
        ServiceException ex =
                assertThrows(ServiceException.class, () -> service.updateEvent(1L, req));

        assertThat(ex.getCode()).isEqualTo(EVENT_NOT_FOUND.getCode());
    }

    @Test
    void deleteEvent_successWhenTaskServiceUnavailable() {
        long id = 88L;
        EventDO event =
                EventDO.builder()
                        .id(id)
                        .userId(1L)
                        .name("Event")
                        .description("desc")
                        .startTime(LocalDateTime.now().minusHours(1))
                        .endTime(LocalDateTime.now().plusHours(1))
                        .location("loc")
                        .status(EventStatusEnum.ACTIVE.getCode())
                        .remark("remark")
                        .build();

        ReflectionTestUtils.setField(service, "taskRpcService", null);
        when(eventMapper.selectById(id)).thenReturn(event);
        when(eventMapper.deleteById(id)).thenReturn(1);
        when(userGroupMapper.delete(any())).thenReturn(1);

        assertThat(service.deleteEvent(id)).isTrue();

        verify(userGroupMapper).delete(any());
        ReflectionTestUtils.setField(service, "taskRpcService", taskRpcService);
    }

    @Test
    void deleteEvent_whenTaskCleanupSucceeds_returnsTrue() {
        long id = 91L;
        EventDO event =
                EventDO.builder()
                        .id(id)
                        .userId(1L)
                        .name("Cleanup")
                        .description("desc")
                        .startTime(LocalDateTime.now())
                        .endTime(LocalDateTime.now().plusHours(2))
                        .status(EventStatusEnum.ACTIVE.getCode())
                        .build();

        when(eventMapper.selectById(id)).thenReturn(event);
        when(eventMapper.deleteById(id)).thenReturn(1);
        when(userGroupMapper.delete(any())).thenReturn(1);

        assertThat(service.deleteEvent(id)).isTrue();

        verify(taskRpcService).deleteTasksByEventId(id);
        verify(userGroupMapper).delete(any());
    }

    @Test
    void deleteEvent_failWhenDeleteReturnsZero() {
        long id = 90L;
        EventDO event =
                EventDO.builder()
                        .id(id)
                        .userId(1L)
                        .name("Event")
                        .description("desc")
                        .startTime(LocalDateTime.now())
                        .endTime(LocalDateTime.now().plusHours(1))
                        .location("loc")
                        .status(EventStatusEnum.ACTIVE.getCode())
                        .remark("remark")
                        .build();
        when(eventMapper.selectById(id)).thenReturn(event);
        when(eventMapper.deleteById(id)).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.deleteEvent(id));

        assertThat(ex.getCode()).isEqualTo(EVENT_DELETE_FAILED.getCode());
    }

    @Test
    void deleteEvent_eventMissing_throwsServiceException() {
        when(eventMapper.selectById(400L)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.deleteEvent(400L));
        assertThat(ex.getCode()).isEqualTo(EVENT_NOT_FOUND.getCode());
    }

    @Test
    void restoreEvent_successfullyRestoresEventAndMemberships() {
        long id = 501L;
        EventDO deleted =
                EventDO.builder()
                        .id(id)
                        .userId(1L)
                        .name("Archived")
                        .description("desc")
                        .startTime(LocalDateTime.now().minusDays(2))
                        .endTime(LocalDateTime.now().minusDays(1))
                        .location("loc")
                        .status(EventStatusEnum.COMPLETED.getCode())
                        .remark("remark")
                        .build();
        deleted.setDeleted(true);

        when(eventMapper.selectRawById(id)).thenReturn(deleted);
        when(eventMapper.restoreById(id)).thenReturn(1);

        assertThat(service.restoreEvent(id)).isTrue();
        verify(userGroupMapper).restoreByEventId(id);
    }

    @Test
    void restoreEvent_whenEventMissing_throwsServiceException() {
        when(eventMapper.selectRawById(9L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.restoreEvent(9L));
        assertThat(ex.getCode()).isEqualTo(EVENT_NOT_FOUND.getCode());
    }

    @Test
    void restoreEvent_whenAlreadyActive_throwsServiceException() {
        EventDO existing =
                EventDO.builder()
                        .id(33L)
                        .userId(1L)
                        .name("Active Event")
                        .description("desc")
                        .startTime(LocalDateTime.now())
                        .endTime(LocalDateTime.now().plusHours(1))
                        .location("loc")
                        .status(EventStatusEnum.ACTIVE.getCode())
                        .remark("remark")
                        .build();
        existing.setDeleted(false);

        when(eventMapper.selectRawById(existing.getId())).thenReturn(existing);

        ServiceException ex =
                assertThrows(ServiceException.class, () -> service.restoreEvent(existing.getId()));
        assertThat(ex.getCode()).isEqualTo(EVENT_NOT_DELETED.getCode());
    }

    @Test
    void restoreEvent_whenRestoreFails_throwsServiceException() {
        EventDO deleted =
                EventDO.builder()
                        .id(66L)
                        .userId(1L)
                        .name("Archived")
                        .description("desc")
                        .startTime(LocalDateTime.now())
                        .endTime(LocalDateTime.now().plusHours(1))
                        .location("loc")
                        .status(EventStatusEnum.ACTIVE.getCode())
                        .remark("remark")
                        .build();
        deleted.setDeleted(true);

        when(eventMapper.selectRawById(deleted.getId())).thenReturn(deleted);
        when(eventMapper.restoreById(deleted.getId())).thenReturn(0);

        ServiceException ex =
                assertThrows(ServiceException.class, () -> service.restoreEvent(deleted.getId()));
        assertThat(ex.getCode()).isEqualTo(EVENT_RESTORE_FAILED.getCode());
    }

    @Test
    void findAssignableGroups_returnsEmptyListWhenNoGroups() {
        long eventId = 12L;
        when(groupApplicationService.getGroupDTOsByEventIds(List.of(eventId)))
                .thenReturn(Collections.emptyMap());

        assertThat(service.findAssignableGroups(eventId)).isEmpty();
    }

    @Test
    void findAssignableGroups_transformsMembersSuccessfully() {
        long eventId = 15L;
        GroupMemberDTO member = GroupMemberDTO.builder().userId(1L).username("Alice").build();
        GroupDTO dto = GroupDTO.builder().id(23L).name("Group A").members(List.of(member)).build();
        when(groupApplicationService.getGroupDTOsByEventIds(List.of(eventId)))
                .thenReturn(Map.of(eventId, List.of(dto)));

        List<EventGroupRespVO> groups = service.findAssignableGroups(eventId);

        assertThat(groups).hasSize(1);
        EventGroupRespVO result = groups.get(0);
        assertThat(result.getId()).isEqualTo(dto.getId());
        assertThat(result.getMembers())
                .singleElement()
                .satisfies(m -> assertThat(m.getUsername()).isEqualTo("Alice"));
    }

    @Test
    void findAssignableGroups_whenMembersNull_returnsEmptyMemberList() {
        long eventId = 99L;
        GroupDTO dto = GroupDTO.builder().id(77L).name("Null Members").members(null).build();
        when(groupApplicationService.getGroupDTOsByEventIds(List.of(eventId)))
                .thenReturn(Map.of(eventId, List.of(dto)));

        List<EventGroupRespVO> groups = service.findAssignableGroups(eventId);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getMembers()).isEmpty();
    }

    @Test
    void fetchParticipantCountsByEventIds_returnsEmptyForNullOrEmptyInput() {
        @SuppressWarnings("unchecked")
        Map<Long, Integer> nullResult =
                ReflectionTestUtils.invokeMethod(
                        service, "fetchParticipantCountsByEventIds", (Object) null);
        assertThat(nullResult).isEmpty();

        @SuppressWarnings("unchecked")
        Map<Long, Integer> emptyResult =
                ReflectionTestUtils.invokeMethod(
                        service, "fetchParticipantCountsByEventIds", List.<Long>of());
        assertThat(emptyResult).isEmpty();
    }

    @Test
    void fetchParticipantCountsByEventIds_filtersInvalidEntries() {
        UserGroupDO valid = UserGroupDO.builder().eventId(1L).userId(10L).build();
        UserGroupDO duplicate = UserGroupDO.builder().eventId(1L).userId(11L).build();
        UserGroupDO nullUser = UserGroupDO.builder().eventId(1L).userId(null).build();
        UserGroupDO nullEvent = UserGroupDO.builder().eventId(null).userId(12L).build();

        when(userGroupMapper.selectList(any()))
                .thenReturn(List.of(valid, duplicate, nullUser, nullEvent));

        @SuppressWarnings("unchecked")
        Map<Long, Integer> counts =
                ReflectionTestUtils.invokeMethod(
                        service, "fetchParticipantCountsByEventIds", List.of(1L));

        assertThat(counts.get(1L)).isEqualTo(2);
    }

    @Test
    void fetchParticipantIdsByEventId_returnsEmptyWhenNull() {
        @SuppressWarnings("unchecked")
        List<Long> result =
                ReflectionTestUtils.invokeMethod(
                        service, "fetchParticipantIdsByEventId", new Object[] {null});
        assertThat(result).isEmpty();
    }

    @Test
    void fetchGroupsByEventIds_convertsNullGroupIdsToNullStrings() {
        GroupRespVO groupWithoutId = GroupRespVO.builder().id(null).name("Nameless").build();
        when(groupApplicationService.getGroupsByEventIds(List.of(1L)))
                .thenReturn(Map.of(1L, List.of(groupWithoutId)));

        @SuppressWarnings("unchecked")
        Map<Long, List<EventRespVO.GroupVO>> result =
                ReflectionTestUtils.invokeMethod(service, "fetchGroupsByEventIds", List.of(1L));

        assertThat(result.get(1L)).singleElement().hasFieldOrPropertyWithValue("id", null);
    }

    @Test
    void fetchTaskStatusesByEventIds_handlesEmptyAndCompletedTasks() {
        TaskDTO completed = TaskDTO.builder().status(TaskStatusEnum.COMPLETED.getStatus()).build();
        TaskDTO pending = TaskDTO.builder().status(TaskStatusEnum.PENDING.getStatus()).build();
        when(taskRpcService.getTasksByEventIds(List.of(1L, 2L)))
                .thenReturn(
                        Map.of(
                                1L, List.of(),
                                2L, List.of(completed, pending)));

        @SuppressWarnings("unchecked")
        Map<Long, EventRespVO.TaskStatusVO> result =
                ReflectionTestUtils.invokeMethod(
                        service, "fetchTaskStatusesByEventIds", List.of(1L, 2L));

        assertThat(result.get(1L).getTotal()).isZero();
        EventRespVO.TaskStatusVO status = result.get(2L);
        assertThat(status.getTotal()).isEqualTo(2);
        assertThat(status.getCompleted()).isEqualTo(1);
        assertThat(status.getRemaining()).isEqualTo(1);
    }
}
