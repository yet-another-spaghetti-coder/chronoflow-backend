package nus.edu.u.framework.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import nus.edu.u.framework.security.audit.SecurityAuditLogger;
import nus.edu.u.framework.security.ratelimit.RateLimiter;
import nus.edu.u.framework.security.satoken.StpPermissionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @author Lu Shuwen
 * @date 2025-10-15
 */
@AutoConfiguration
public class SecurityAutoConfiguration {

    @Bean
    public StpPermissionHandler stpPermissionHandler() {
        return new StpPermissionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityAuditLogger securityAuditLogger(
            StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new SecurityAuditLogger(redisTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimiter rateLimiter(StringRedisTemplate redisTemplate) {
        return new RateLimiter(redisTemplate);
    }
}
