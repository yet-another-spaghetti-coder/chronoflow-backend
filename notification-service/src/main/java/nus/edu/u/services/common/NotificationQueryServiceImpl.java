package nus.edu.u.services.common;

import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.domain.dataObject.common.NotificationEventDO;
import nus.edu.u.domain.dto.common.NotificationDetailRespDTO;
import nus.edu.u.repositories.common.NotificationEventRepository;
import nus.edu.u.shared.rpc.events.EventRpcService;
import nus.edu.u.shared.rpc.task.TaskRpcService;
import nus.edu.u.shared.rpc.user.UserRpcService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationQueryServiceImpl implements NotificationQueryService {

    private final NotificationEventRepository notificationEventRepo;

    @DubboReference(check = false)
    private final UserRpcService userRpc;

    @DubboReference(check = false)
    private final TaskRpcService taskRpc;

    @DubboReference(check = false)
    private final EventRpcService eventRpc;

    @Transactional(readOnly = true)
    @Override
    public NotificationDetailRespDTO getDetail(String notifId, String currentUserId) {
        if (notifId == null || notifId.isBlank()) {
            throw new IllegalArgumentException("notifId is required");
        }
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new IllegalArgumentException("currentUserId is required");
        }

        // 1) Load + ownership check
        NotificationEventDO notif =
                notificationEventRepo
                        .findByIdAndRecipientUserId(notifId, currentUserId)
                        .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

        // 2) Build notificationEvent section
        NotificationDetailRespDTO.NotificationEventDTO eventDto =
                NotificationDetailRespDTO.NotificationEventDTO.builder()
                        .id(notif.getId())
                        .type(notif.getType().name())
                        .createdAt(toSgLocalDateTime(notif.getCreatedAt()))
                        .read(Boolean.TRUE.equals(notif.getRead()))
                        .readAt(notif.getReadAt())
                        .build();

        // 3) Resolve TASK (for NEW_TASK_ASSIGN) from objectType/objectId stored in notif table
        if (!"TASK".equalsIgnoreCase(notif.getObjectType())) {
            throw new IllegalArgumentException("Unsupported objectType: " + notif.getObjectType());
        }

        Long taskId =
                parseLongOrThrow(notif.getObjectId(), "Invalid taskId: " + notif.getObjectId());

        var task = taskRpc.getTaskDetail(taskId); // shared.rpc.task.TaskDTO
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }

        NotificationDetailRespDTO.TaskResolvedDTO taskResolved =
                NotificationDetailRespDTO.TaskResolvedDTO.builder()
                        .id(task.getId())
                        .eventId(task.getEventId())
                        .name(task.getName())
                        .description(task.getDescription())
                        .remark(task.getRemark())
                        .startTime(task.getStartTime())
                        .endTime(task.getEndTime())
                        .build();

        // 4) Resolve actor + event sequentially (best effort)
        NotificationDetailRespDTO.ActorDTO actor = resolveActorBestEffort(notif.getActorId());

        NotificationDetailRespDTO.EventResolvedDTO eventResolved =
                resolveEventBestEffort(taskResolved.getEventId());

        // 5) Assemble final response (no objectRef)
        return NotificationDetailRespDTO.builder()
                .notificationEvent(eventDto)
                .actor(actor)
                .task(taskResolved)
                .event(eventResolved)
                .build();
    }

    private LocalDateTime toSgLocalDateTime(java.time.Instant instant) {
        if (instant == null) return null;
        return LocalDateTime.ofInstant(instant, ZoneId.of("Asia/Singapore"));
    }

    private NotificationDetailRespDTO.ActorDTO resolveActorBestEffort(String actorIdStr) {
        if (actorIdStr == null || actorIdStr.isBlank()) {
            return NotificationDetailRespDTO.ActorDTO.builder().id(null).name(null).build();
        }

        Long actorId = parseLongOrNull(actorIdStr);
        if (actorId == null) {
            return NotificationDetailRespDTO.ActorDTO.builder().id(actorIdStr).name(null).build();
        }

        try {
            var profile = userRpc.getUserById(actorId); // UserProfileDTO
            return NotificationDetailRespDTO.ActorDTO.builder()
                    .id(actorIdStr)
                    .name(profile != null ? profile.getName() : null)
                    .build();
        } catch (Exception e) {
            log.warn("Actor resolve failed actorId={}: {}", actorIdStr, e.getMessage());
            return NotificationDetailRespDTO.ActorDTO.builder().id(actorIdStr).name(null).build();
        }
    }

    private NotificationDetailRespDTO.EventResolvedDTO resolveEventBestEffort(Long eventId) {
        if (eventId == null) return null;

        try {
            String name = eventRpc.getEventName(eventId);
            return NotificationDetailRespDTO.EventResolvedDTO.builder()
                    .id(eventId)
                    .name(name)
                    .build();
        } catch (Exception e) {
            log.warn("Event resolve failed eventId={}: {}", eventId, e.getMessage());
            return NotificationDetailRespDTO.EventResolvedDTO.builder()
                    .id(eventId)
                    .name(null)
                    .build();
        }
    }

    private Long parseLongOrThrow(String s, String msg) {
        Long v = parseLongOrNull(s);
        if (v == null) throw new IllegalArgumentException(msg);
        return v;
    }

    private Long parseLongOrNull(String s) {
        if (s == null) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
