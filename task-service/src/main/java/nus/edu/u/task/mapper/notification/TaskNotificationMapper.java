package nus.edu.u.task.mapper.notification;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import nus.edu.u.shared.rpc.notification.dto.common.NotificationRequestDTO;
import nus.edu.u.shared.rpc.notification.dto.task.NewTaskAssignmentDTO;
import nus.edu.u.shared.rpc.notification.enums.NotificationChannel;
import nus.edu.u.shared.rpc.notification.enums.NotificationEventType;
import nus.edu.u.shared.rpc.notification.enums.NotificationObjectType;

public class TaskNotificationMapper {

    /** Build the same idempotent eventId for every channel. */
    private static String idempotentEventId(NewTaskAssignmentDTO req) {
        return NotificationEventType.buildEventId(
                NotificationEventType.NEW_TASK_ASSIGN,
                "task",
                req.getTaskId(),
                "event",
                req.getEventId(),
                "assignee",
                req.getAssigneeUserId());
    }

    /** EMAIL */
    public static NotificationRequestDTO newTaskAssignmentToEmailNotification(
            NewTaskAssignmentDTO req) {
        Map<String, Object> vars =
                Map.of(
                        "taskId", req.getTaskId(),
                        "eventId", req.getEventId(),
                        "assigneeUserId", req.getAssigneeUserId(),
                        "assigneeEmail", req.getAssigneeEmail(),
                        "assignerName", req.getAssignerName(),
                        "taskName", req.getTaskName(),
                        "eventName", req.getEventName(),
                        "description", req.getDescription());

        return NotificationRequestDTO.builder()
                .channel(NotificationChannel.EMAIL)
                .to(req.getAssigneeEmail())
                .userId(req.getAssigneeUserId())
                .recipientKey("email:" + req.getAssigneeEmail())
                .templateId("new-task-assigned")
                .variables(vars)
                .locale(Locale.ENGLISH)
                .attachments(List.of())
                .eventId(idempotentEventId(req))
                .type(NotificationEventType.NEW_TASK_ASSIGN)
                .build();
    }

    /** PUSH */
    public static NotificationRequestDTO newTaskAssignmentToPushNotification(
            NewTaskAssignmentDTO req) {

        return NotificationRequestDTO.builder()
                .channel(NotificationChannel.PUSH)
                .type(NotificationEventType.NEW_TASK_ASSIGN)
                .eventId(idempotentEventId(req))
                .recipientUserId(req.getAssigneeUserId())
                .userId(req.getAssigneeUserId()) // temporary
                .actorId(req.getAssignerUserId())
                .objectType(NotificationObjectType.TASK)
                .objectId(String.valueOf(req.getTaskId()))
                .title("Notification")
                .previewText("A new task has been assigned to you")
                .build();
    }

    public static NotificationRequestDTO newTaskAssignmentToWsNotification(
            NewTaskAssignmentDTO req) {
        Map<String, Object> vars =
                Map.of(
                        "taskId", req.getTaskId(),
                        "eventId", req.getEventId(),
                        "assigneeUserId", req.getAssigneeUserId(),
                        "assignerName", req.getAssignerName(),
                        "taskName", req.getTaskName(),
                        "eventName", req.getEventName(),
                        "description", req.getDescription(),
                        "deepLink",
                                String.format(
                                        "/events/%s/tasks/%s", req.getEventId(), req.getTaskId()));

        return NotificationRequestDTO.builder()
                .channel(NotificationChannel.WS)
                .userId(req.getAssigneeUserId())
                .templateId("new-task-assigned")
                .variables(vars)
                .locale(Locale.ENGLISH)
                .eventId(idempotentEventId(req))
                .type(NotificationEventType.NEW_TASK_ASSIGN)
                .build();
    }
}
