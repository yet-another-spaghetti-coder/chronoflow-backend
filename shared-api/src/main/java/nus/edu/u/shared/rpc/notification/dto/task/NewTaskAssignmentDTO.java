package nus.edu.u.shared.rpc.notification.dto.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a domain-level "Task Assignment" event.
 *
 * <p>This is not tied to any specific notification channel — it's the pure business payload passed
 * from the Task domain to the Notification module.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewTaskAssignmentDTO {
    private String taskId;
    private String eventId;
    private String assignerName;
    private String assigneeUserId;
    private String assignerUserId;
    private String assigneeEmail;
    private String taskName;
    private String eventName;
    private String description;
}
