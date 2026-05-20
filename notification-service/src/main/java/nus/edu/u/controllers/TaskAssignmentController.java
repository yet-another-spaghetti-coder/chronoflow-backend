package nus.edu.u.controllers;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.domain.dto.common.NewTaskAssignmentDTO;
import nus.edu.u.services.domains.task.TaskAssignmentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller to test and trigger task assignment notifications across multiple channels (push +
 * email).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/task")
@Slf4j
public class TaskAssignmentController {

    private final TaskAssignmentService taskAssignmentService;

    /**
     * Sends both push and email notifications when a new task is assigned.
     *
     * <p>Example: POST /api/v1/task/assign/notify { "taskId": "42", "assigneeUserId":
     * "1976859664874131458", "assigneeEmail": "john.doe@example.com", "assignerName": "Alice Tan",
     * "taskName": "Prepare Event Slides", "eventName": "ChronoFlow Kickoff" }
     */
    @PostMapping("/assign/notify")
    public String notifyAssignment(@RequestBody NewTaskAssignmentDTO dto) {
        Map<String, String> result = taskAssignmentService.notifyNewTaskAllChannels(dto);

        log.info(
                "Task assignment notify request for taskId={} assigneeUserId={} email={} result={}",
                dto.getTaskId(),
                dto.getAssigneeUserId(),
                dto.getAssigneeEmail(),
                result);

        return "Successfully assigned task '" + dto.getTaskName() + "'";
    }
}
