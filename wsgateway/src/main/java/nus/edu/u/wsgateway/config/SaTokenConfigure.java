package nus.edu.u.wsgateway.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defense-in-depth: even though the API gateway already validates Sa-Token before forwarding to
 * wsgateway, register a SaReactorFilter here so:
 *
 * <ul>
 *   <li>{@code SaReactorSyncHolder} context is populated, making {@code StpUtil} usable from
 *       reactive controllers (e.g. /ws/token, /ws/feed).
 *   <li>If wsgateway is ever exposed directly (bypassing the gateway), it still enforces auth.
 * </ul>
 *
 * <p>The exclude list mirrors the gateway's public surface: the WS upgrade endpoint ({@code /ws})
 * is unauthenticated by design (auth happens in-band via the first AUTH frame, see {@link
 * nus.edu.u.wsgateway.socket.WsHandler}), and internal push is reachable from notification-service.
 */
@Slf4j
@Configuration
public class SaTokenConfigure {

    @Bean
    public SaReactorFilter wsgatewaySaReactorFilter() {
        return new SaReactorFilter()
                .addInclude("/**")
                .addExclude(
                        "/ws", // WS upgrade — auth via AUTH-first JWT frame
                        "/ws/internal/**", // Service-to-service push (Phase 8b: token check)
                        "/actuator/**",
                        "/favicon.ico",
                        "/v3/**")
                .setAuth(
                        obj ->
                                SaRouter.notMatch(SaHttpMethod.OPTIONS)
                                        .free(r -> StpUtil.checkLogin()))
                .setError(
                        e -> {
                            SaRequest request = SaHolder.getRequest();
                            log.info(
                                    "ws-gw auth reject: {} {} {}",
                                    request.getMethod(),
                                    request.getUrl(),
                                    e.getMessage());
                            return SaResult.error(e.getMessage());
                        });
    }
}
