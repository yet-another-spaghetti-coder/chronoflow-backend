package nus.edu.u.wsgateway.api;

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
import org.springframework.web.bind.annotation.*;
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
    // INTERNAL (NotificationSvc -> Gateway)
    // =========================
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
    // PUBLIC API (Client -> Gateway)
    // =========================
    @GetMapping("/feed")
    public Flux<NotificationFeedDoc> feed(
            @RequestParam("userId") String userId,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "beforeEpochMs", required = false) Long beforeEpochMs) {
        log.info(
                "Fetching notification feed for userId={} limit={} beforeEpochMs={}",
                userId,
                limit,
                beforeEpochMs);
        Instant before = (beforeEpochMs == null) ? null : Instant.ofEpochMilli(beforeEpochMs);
        return feedService.page(userId, limit, before);
    }

    @GetMapping("/unread/{userId}")
    public Mono<Map<String, Long>> unread(@PathVariable("userId") String userId) {
        log.info("Fetching unread notification count for userId={}", userId);
        return feedService.unreadCount(userId).map(c -> Map.of("unread", c));
    }

    @PostMapping("/mark-opened")
    public Mono<Map<String, Object>> markOpened(@RequestBody MarkSeenRequestDTO req) {
        log.info(
                "Marking notifications opened for userId={} count={}",
                req.getUserId(),
                req.getNotificationIds() != null ? req.getNotificationIds().size() : 0);
        return feedService
                .markOpened(req.getUserId(), req.getNotificationIds())
                .map(updated -> Map.of("updated", updated));
    }
}
