package nus.edu.u.event.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.stp.StpUtil;
import java.util.List;
import nus.edu.u.common.core.domain.CommonResult;
import nus.edu.u.event.domain.dto.event.EventCreateReqVO;
import nus.edu.u.event.domain.dto.event.EventGroupRespVO;
import nus.edu.u.event.domain.dto.event.EventRespVO;
import nus.edu.u.event.domain.dto.event.EventUpdateReqVO;
import nus.edu.u.event.domain.dto.event.UpdateEventRespVO;
import nus.edu.u.event.service.EventApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    @Mock private EventApplicationService eventApplicationService;

    @InjectMocks private EventController controller;

    @BeforeEach
    void setUp() {
        SaTokenContextMockUtil.setMockContext();
    }

    @AfterEach
    void tearDown() {
        try {
            if (StpUtil.isLogin()) {
                StpUtil.logout();
            }
        } catch (Exception ignored) {
        }
        SaTokenContextMockUtil.clearContext();
    }

    @Test
    void create_setsOrganizerFromLogin() {
        StpUtil.login(100L);
        EventCreateReqVO req = new EventCreateReqVO();
        req.setEventName("Board Game Night");
        EventRespVO respVO = new EventRespVO();
        when(eventApplicationService.createEvent(any(EventCreateReqVO.class))).thenReturn(respVO);

        CommonResult<EventRespVO> result = controller.create(req);

        assertThat(result.getData()).isSameAs(respVO);
        ArgumentCaptor<EventCreateReqVO> captor = ArgumentCaptor.forClass(EventCreateReqVO.class);
        verify(eventApplicationService).createEvent(captor.capture());
        assertThat(captor.getValue().getOrganizerId()).isEqualTo(100L);
    }

    @Test
    void getById_returnsEvent() {
        EventRespVO resp = new EventRespVO();
        when(eventApplicationService.getEvent(5L)).thenReturn(resp);

        CommonResult<EventRespVO> result = controller.getById(5L);

        assertThat(result.getData()).isSameAs(resp);
        verify(eventApplicationService).getEvent(5L);
    }

    @Test
    void update_delegatesToService() {
        EventUpdateReqVO req = new EventUpdateReqVO();
        UpdateEventRespVO resp = new UpdateEventRespVO();
        when(eventApplicationService.updateEvent(eq(8L), any(EventUpdateReqVO.class)))
                .thenReturn(resp);

        CommonResult<UpdateEventRespVO> result = controller.update(8L, req);

        assertThat(result.getData()).isSameAs(resp);
        verify(eventApplicationService).updateEvent(8L, req);
    }

    @Test
    void delete_returnsServiceResult() {
        when(eventApplicationService.deleteEvent(9L)).thenReturn(true);

        CommonResult<Boolean> result = controller.delete(9L);

        assertThat(result.getData()).isTrue();
        verify(eventApplicationService).deleteEvent(9L);
    }

    @Test
    void restore_returnsServiceResult() {
        when(eventApplicationService.restoreEvent(10L)).thenReturn(true);

        CommonResult<Boolean> result = controller.restore(10L);

        assertThat(result.getData()).isTrue();
        verify(eventApplicationService).restoreEvent(10L);
    }

    @Test
    void assignableGroups_delegatesToApplicationService() {
        List<EventGroupRespVO> groups = List.of(new EventGroupRespVO());
        when(eventApplicationService.findAssignableGroups(11L)).thenReturn(groups);

        CommonResult<List<EventGroupRespVO>> result = controller.assignableGroups(11L);

        assertThat(result.getData()).isSameAs(groups);
        verify(eventApplicationService).findAssignableGroups(11L);
    }
}
