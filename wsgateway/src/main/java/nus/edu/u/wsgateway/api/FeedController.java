package nus.edu.u.wsgateway.api;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.wsgateway.domain.NotificationFeedDoc;
import nus.edu.u.wsgateway.dto.MarkSeenRequestDTO;
import nus.edu.u.wsgateway.dto.WsPushRequestDTO;
import nus.edu.u.wsgateway.service.FeedAndPushService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ws")
@Slf4j
public class FeedController {

    private final FeedAndPushService feedService;

    // =========================
    // INTERNAL (notification-service -> wsgateway)
    // =========================
    // Phase 8b follow-up: require X-Internal-Service-Token shared secret here. For now this
    // endpoint is reachable from any caller the gateway forwards in, hence the follow-up task.
    @PostMapping("/internal/push")
    public Mono<ResponseEntity<Map<String, Object>>> push(@RequestBody WsPushRequestDTO req) {
        log.info(
                "Incoming internal push for userId={} type={} eventId={}",
                req.getUserId(),
                req.getType(),
                req.getEventId());
        return feedService
                .createOrTouchAndPush(req)
                .map(
                        doc -> {
                            String status = (doc.getDeliveredAt() != null) ? "DELIVERED" : "QUEUED";
                            Map<String, Object> body = new HashMap<>();
                            body.put("status", status);
                            if (doc.getId() != null) body.put("id", doc.getId());
                            return ResponseEntity.accepted().body(body);
                        });
    }

    // =========================
    // PUBLIC API (Client -> Gateway -> wsgateway)
    // =========================
    // PLS 03 / OWASP API1 (BOLA): the authenticated identity is the source of truth. Any
    // client-supplied `userId` must match StpUtil.getLoginIdAsString() — otherwise we'd let user A
    // read user B's notifications. The Sa-Token session is enforced by both the API gateway and
    // wsgateway's SaReactorFilter; the controller adds the object-level identity check.

    @GetMapping("/feed")
    public Flux<NotificationFeedDoc> feed(
            @RequestParam(name = "userId", required = false) String requestedUserId,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "beforeEpochMs", required = false) Long beforeEpochMs) {
        String userId = currentUserOrReject("/ws/feed", requestedUserId);
        log.info(
                "Fetching notification feed userId={} limit={} before={}",
                userId,
                limit,
                beforeEpochMs);
        Instant before = (beforeEpochMs == null) ? null : Instant.ofEpochMilli(beforeEpochMs);
        return feedService.page(userId, limit, before);
    }

    @GetMapping("/unread/{userId}")
    public Mono<Map<String, Long>> unread(@PathVariable("userId") String requestedUserId) {
        String userId = currentUserOrReject("/ws/unread", requestedUserId);
        log.info("Fetching unread notification count userId={}", userId);
        return feedService.unreadCount(userId).map(c -> Map.of("unread", c));
    }

    @PostMapping("/mark-opened")
    public Mono<Map<String, Object>> markOpened(@RequestBody MarkSeenRequestDTO req) {
        String userId = currentUserOrReject("/ws/mark-opened", req.getUserId());
        log.info(
                "Marking notifications opened userId={} count={}",
                userId,
                req.getNotificationIds() != null ? req.getNotificationIds().size() : 0);
        return feedService
                .markOpened(userId, req.getNotificationIds())
                .map(updated -> Map.of("updated", updated));
    }

    /**
     * Resolve the authenticated user and reject if the caller tried to act on behalf of a different
     * user. Logs the attempt at WARN since this is a sign of either a buggy client or an active
     * BOLA probe.
     */
    private static String currentUserOrReject(String endpoint, String requestedUserId) {
        StpUtil.checkLogin();
        String authedUserId = StpUtil.getLoginIdAsString();
        if (requestedUserId != null
                && !requestedUserId.isBlank()
                && !requestedUserId.equals(authedUserId)) {
            log.warn(
                    "[WS] BOLA attempt at {}: authed={} requested={}",
                    endpoint,
                    authedUserId,
                    requestedUserId);
            throw new NotPermissionException("userId mismatch");
        }
        return authedUserId;
    }
}
