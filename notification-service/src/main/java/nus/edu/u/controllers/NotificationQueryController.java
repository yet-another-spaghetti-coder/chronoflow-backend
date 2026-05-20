package nus.edu.u.controllers;

import static nus.edu.u.common.core.domain.CommonResult.success;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.common.core.domain.CommonResult;
import nus.edu.u.domain.dto.common.NotificationDetailRespDTO;
import nus.edu.u.services.common.NotificationQueryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationQueryController {

    private final NotificationQueryService notificationQueryService;

    @GetMapping("/{notifId}")
    public CommonResult<NotificationDetailRespDTO> getNotificationDetail(
            @PathVariable("notifId") String notifId) {
        StpUtil.checkLogin();
        String currentUserId = String.valueOf(StpUtil.getLoginIdAsLong());

        log.info(
                "Fetching notification detail for notifId={} currentUserId={}",
                notifId,
                currentUserId);
        return success(notificationQueryService.getDetail(notifId, currentUserId));
    }
}
