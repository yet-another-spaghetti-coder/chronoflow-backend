package nus.edu.u.user.service.auth;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.framework.mybatis.MybatisPlusConfig;
import nus.edu.u.user.domain.dataobject.user.UserDO;
import nus.edu.u.user.service.user.UserService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for bidirectional mapping between Firebase UID and internal user ID.
 * Uses Redis caching with database fallback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FirebaseUserMappingService {

    private final StringRedisTemplate redisTemplate;
    private final UserService userService;

    private static final String FIREBASE_TO_INTERNAL_KEY = "firebase:uid:to:internal:";
    private static final String INTERNAL_TO_FIREBASE_KEY = "firebase:internal:to:uid:";
    private static final long MAPPING_TTL_SECONDS = 86400; // 24 hours

    /**
     * Get internal user ID from Firebase UID.
     *
     * @param firebaseUid Firebase UID
     * @return Internal user ID or null if not found
     */
    public Long getInternalUserId(String firebaseUid) {
        if (firebaseUid == null) {
            return null;
        }

        // Check cache first
        String cached = redisTemplate.opsForValue().get(FIREBASE_TO_INTERNAL_KEY + firebaseUid);
        if (cached != null) {
            return Long.parseLong(cached);
        }

        // Query database - bypass tenant filter since user is not authenticated yet
        UserDO user = MybatisPlusConfig.executeWithoutTenantFilter(
                () -> userService.getUserByFirebaseUid(firebaseUid));
        if (user != null) {
            // Cache the mapping
            createMapping(firebaseUid, user.getId());
            return user.getId();
        }

        return null;
    }

    /**
     * Get Firebase UID from internal user ID.
     *
     * @param internalUserId Internal user ID
     * @return Firebase UID or null if not found
     */
    public String getFirebaseUid(Long internalUserId) {
        if (internalUserId == null) {
            return null;
        }

        // Check cache first
        String cached = redisTemplate.opsForValue().get(INTERNAL_TO_FIREBASE_KEY + internalUserId);
        if (cached != null) {
            return cached;
        }

        // Query database
        UserDO user = userService.selectUserById(internalUserId);
        if (user != null && user.getFirebaseUid() != null) {
            // Cache the mapping
            createMapping(user.getFirebaseUid(), internalUserId);
            return user.getFirebaseUid();
        }

        return null;
    }

    /**
     * Create bidirectional mapping in cache.
     *
     * @param firebaseUid Firebase UID
     * @param internalUserId Internal user ID
     */
    public void createMapping(String firebaseUid, Long internalUserId) {
        redisTemplate
                .opsForValue()
                .set(
                        FIREBASE_TO_INTERNAL_KEY + firebaseUid,
                        internalUserId.toString(),
                        MAPPING_TTL_SECONDS,
                        TimeUnit.SECONDS);
        redisTemplate
                .opsForValue()
                .set(
                        INTERNAL_TO_FIREBASE_KEY + internalUserId,
                        firebaseUid,
                        MAPPING_TTL_SECONDS,
                        TimeUnit.SECONDS);
        log.debug("Created Firebase mapping: {} <-> {}", firebaseUid, internalUserId);
    }

    /**
     * Remove mapping from cache.
     *
     * @param firebaseUid Firebase UID
     * @param internalUserId Internal user ID
     */
    public void removeMapping(String firebaseUid, Long internalUserId) {
        if (firebaseUid != null) {
            redisTemplate.delete(FIREBASE_TO_INTERNAL_KEY + firebaseUid);
        }
        if (internalUserId != null) {
            redisTemplate.delete(INTERNAL_TO_FIREBASE_KEY + internalUserId);
        }
    }
}
