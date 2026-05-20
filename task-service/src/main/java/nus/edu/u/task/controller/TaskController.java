package nus.edu.u.task.controller;

import static nus.edu.u.common.constant.PermissionConstants.CREATE_TASK;
import static nus.edu.u.common.constant.PermissionConstants.DELETE_TASK;
import static nus.edu.u.common.constant.PermissionConstants.QUERY_TASK;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.common.core.domain.CommonResult;
import nus.edu.u.task.domain.vo.task.TaskCreateReqVO;
import nus.edu.u.task.domain.vo.task.TaskDashboardRespVO;
import nus.edu.u.task.domain.vo.task.TaskRespVO;
import nus.edu.u.task.domain.vo.task.TaskUpdateReqVO;
import nus.edu.u.task.domain.vo.taskLog.TaskLogRespVO;
import nus.edu.u.task.service.TaskApplicationService;
import nus.edu.u.task.service.TaskLogApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tasks")
@Validated
@RequiredArgsConstructor
@Slf4j
public class TaskController {

    private final TaskApplicationService taskApplicationService;
    private final TaskLogApplicationService taskLogApplicationService;

    // super testing

    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    @PostMapping("/send")
    public ResponseEntity<?> sendRawEmailPubSubMessage() {
        try {
            // 1️⃣ Simple raw JSON payload (no DTO, no template)
            String rawJson =
                    """
        {
          "to": "chenyuliang1121@gmail.com",
          "recipientKey": "email:chenyuliang1121@gmail.com",
          "subject": "ChronoFlow Test Email",
          "body": "Hello! This is a raw Pub/Sub email test from TaskService.",
          "eventId": "demo-email-raw-00123",
          "type": "MEMBER_INVITE",
          "templateId": "member-invite"
        }
        """;

            // 2️⃣ Publish to Pub/Sub topic
            String topicName = "chronoflow-notification"; // must match your GCP topic
            pubSubTemplate.publish(topicName, rawJson);

            log.info("📤 Published raw email message to topic {}: {}", topicName, rawJson);
            return ResponseEntity.ok(
                    Map.of(
                            "status", "PUBLISHED",
                            "topic", topicName,
                            "payload", rawJson));

        } catch (Exception e) {
            log.error("❌ Failed to publish raw Pub/Sub email", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @SaCheckPermission(CREATE_TASK)
    @PostMapping("{eventId}")
    public CommonResult<TaskRespVO> create(
            @PathVariable("eventId") Long eventId, @Valid @ModelAttribute TaskCreateReqVO request) {
        log.info("Creating task for eventId={} with title={}", eventId, request.getName());
        TaskRespVO resp = taskApplicationService.createTask(eventId, request);
        return CommonResult.success(resp);
    }

    @GetMapping("{eventId}/{taskId}")
    public CommonResult<TaskRespVO> getTask(
            @PathVariable("eventId") Long eventId, @PathVariable("taskId") Long taskId) {
        log.info("Fetching task taskId={} for eventId={}", taskId, eventId);
        TaskRespVO resp = taskApplicationService.getTask(eventId, taskId);
        return CommonResult.success(resp);
    }

    @GetMapping("/{eventId}")
    public CommonResult<List<TaskRespVO>> listByEvent(@PathVariable("eventId") Long eventId) {
        log.info("Listing tasks for eventId={}", eventId);
        List<TaskRespVO> resp = taskApplicationService.listTasksByEvent(eventId);
        return CommonResult.success(resp);
    }

    @GetMapping("/dashboard")
    public CommonResult<TaskDashboardRespVO> dashboard() {
        Long memberId = StpUtil.getLoginIdAsLong();
        log.info("Fetching task dashboard for memberId={}", memberId);
        TaskDashboardRespVO resp = taskApplicationService.getByMemberId(memberId);
        return CommonResult.success(resp);
    }

    @PatchMapping("{eventId}/{taskId}")
    public CommonResult<TaskRespVO> update(
            @PathVariable("eventId") Long eventId,
            @PathVariable("taskId") Long taskId,
            @Valid @ModelAttribute TaskUpdateReqVO request) {
        log.info(
                "Updating task taskId={} for eventId={} with type={}",
                taskId,
                eventId,
                request.getType());
        TaskRespVO resp =
                taskApplicationService.updateTask(eventId, taskId, request, request.getType());
        return CommonResult.success(resp);
    }

    @SaCheckPermission(DELETE_TASK)
    @DeleteMapping("/{eventId}/{taskId}")
    public CommonResult<Boolean> delete(
            @PathVariable("eventId") Long eventId, @PathVariable("taskId") Long taskId) {
        log.info("Deleting task taskId={} for eventId={}", taskId, eventId);
        taskApplicationService.deleteTask(eventId, taskId);
        return CommonResult.success(Boolean.TRUE);
    }

    @SaCheckPermission(QUERY_TASK)
    @GetMapping("/{eventId}/log/{taskId}")
    public CommonResult<List<TaskLogRespVO>> logs(
            @PathVariable("eventId") Long eventId, @PathVariable("taskId") Long taskId) {
        log.info("Fetching task logs for taskId={} eventId={}", taskId, eventId);
        List<TaskLogRespVO> resp = taskLogApplicationService.getTaskLog(taskId);
        return CommonResult.success(resp);
    }
}
