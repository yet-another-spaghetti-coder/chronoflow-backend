package nus.edu.u.services.common;

import nus.edu.u.domain.dataObject.common.NotificationEventDO;
import nus.edu.u.domain.dto.common.NotificationRequestDTO;

public interface NotificationEventService {
    public NotificationEventDO createFromRequest(NotificationRequestDTO req);
}
