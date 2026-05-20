package nus.edu.u.services.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.domain.dataObject.common.NotificationEventDO;
import nus.edu.u.repositories.common.NotificationEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationEventServiceImpl implements NotificationEventService {

    private final NotificationEventRepository repo;

    @Override
    public NotificationEventDO createFromRequest(
            nus.edu.u.domain.dto.common.NotificationRequestDTO req) {
        if (req == null) throw new IllegalArgumentException("request is required");
        if (req.getRecipientUserId() == null || req.getRecipientUserId().isBlank())
            throw new IllegalArgumentException("recipientUserId is required");
        if (req.getType() == null) throw new IllegalArgumentException("type is required");

        log.info("Creating notification event" + req);

        NotificationEventDO row =
                NotificationEventDO.builder()
                        .actorId(req.getActorId())
                        .eventId(req.getEventId())
                        .recipientUserId(req.getRecipientUserId())
                        .type(req.getType())
                        .title(req.getTitle())
                        .actorId(req.getActorId())
                        .objectType(req.getObjectType() == null ? null : req.getObjectType().name())
                        .objectId(req.getObjectId())
                        .previewText(req.getPreviewText())
                        .read(false)
                        .build();

        return repo.save(row);
    }
}
