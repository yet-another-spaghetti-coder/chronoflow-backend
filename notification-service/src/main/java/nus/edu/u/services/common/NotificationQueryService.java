package nus.edu.u.services.common;

import nus.edu.u.domain.dto.common.NotificationDetailRespDTO;

public interface NotificationQueryService {
    public NotificationDetailRespDTO getDetail(String notifId, String currentUserId);
}
