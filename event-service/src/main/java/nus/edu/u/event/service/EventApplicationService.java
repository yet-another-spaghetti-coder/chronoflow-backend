package nus.edu.u.event.service;

import java.util.List;
import nus.edu.u.event.domain.dto.event.EventCreateReqVO;
import nus.edu.u.event.domain.dto.event.EventGroupRespVO;
import nus.edu.u.event.domain.dto.event.EventRespVO;
import nus.edu.u.event.domain.dto.event.EventUpdateReqVO;
import nus.edu.u.event.domain.dto.event.UpdateEventRespVO;

public interface EventApplicationService {

    EventRespVO createEvent(EventCreateReqVO reqVO);

    EventRespVO getEvent(Long eventId);

    List<EventRespVO> list();

    UpdateEventRespVO updateEvent(Long id, EventUpdateReqVO reqVO);

    boolean deleteEvent(Long id);

    boolean restoreEvent(Long id);

    List<EventGroupRespVO> findAssignableGroups(Long eventId);
}
