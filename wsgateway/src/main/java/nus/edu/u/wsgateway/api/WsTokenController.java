package nus.edu.u.wsgateway.api;

import cn.dev33.satoken.reactor.context.SaReactorHolder;
import cn.dev33.satoken.stp.StpUtil;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.wsgateway.security.WsJwtService;
import nus.edu.u.wsgateway.security.WsTokenGuard;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Mints short-lived WebSocket-only JWTs after Sa-Token session validation.
 *
 * <p>The path {@code /ws/token} is NOT in {@code gateway.white-list}, so the gateway's
 * SaReactorFilter runs StpUtil.checkLogin() before the request reaches this controller. The
 * defensive checkLogin() call here is a second layer in case a future change exposes this service
 * directly.
 *
 * <p>PLS 03: CSWH mitigation — explicit per-connection auth token, not cookie-only trust.
 */
@Slf4j
@RestController
@RequestMapping("/ws")
@RequiredArgsConstructor
public class WsTokenController {

    private final WsJwtService jwtService;
    private final WsTokenGuard tokenGuard;

    @PostMapping("/token")
    public Mono<ResponseEntity<Map<String, Object>>> issueToken() {
        return SaReactorHolder.sync(
                () -> {
                    StpUtil.checkLogin();
                    String userId = StpUtil.getLoginIdAsString();
                    if (!tokenGuard.allowMint(userId)) {
                        log.warn("[WS] token mint rate limited userId={}", userId);
                        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                .body(Map.<String, Object>of("error", "rate_limited"));
                    }
                    WsJwtService.Minted minted = jwtService.mint(userId);
                    log.debug(
                            "[WS] minted WS JWT userId={} jti={} exp={}",
                            userId,
                            minted.jti(),
                            minted.expEpochSeconds());
                    return ResponseEntity.ok(
                            Map.of("token", minted.token(), "exp", minted.expEpochSeconds()));
                });
    }
}
