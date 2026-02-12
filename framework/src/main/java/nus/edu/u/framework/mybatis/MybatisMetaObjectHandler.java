package nus.edu.u.framework.mybatis;

import static nus.edu.u.framework.mybatis.MybatisPlusConfig.getCurrentTenantId;
import static nus.edu.u.framework.mybatis.MybatisPlusConfig.isTenantFilterBypassed;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * Config to fill common properties automatically
 *
 * @author Lu Shuwen
 * @date 2025-09-10
 */
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        // Fill time automatically
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());

        // Fill user automatically
        String currentUser = getCurrentUserId();
        this.strictInsertFill(metaObject, "creator", String.class, currentUser);
        this.strictInsertFill(metaObject, "updater", String.class, currentUser);

        if (metaObject.hasSetter("tenantId")) {
            Object tenantId = metaObject.getValue("tenantId");
            if (ObjectUtil.isNull(tenantId)) {
                // Skip auto-fill if tenant filter is bypassed (e.g., during Firebase registration)
                // The caller is responsible for setting tenant_id explicitly in this case
                if (!isTenantFilterBypassed()) {
                    this.strictInsertFill(metaObject, "tenant_id", Long.class, getCurrentTenantId());
                }
            }
        }
        this.strictInsertFill(metaObject, "deleted", Boolean.class, false);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updater", String.class, getCurrentUserId());
    }

    private String getCurrentUserId() {
        // Get login user id
        try {
            Object userIdObject = StpUtil.getLoginId();
            if (ObjectUtil.isNotNull(userIdObject)) {
                return String.valueOf(userIdObject);
            }
        } catch (Exception ignored) {
        }
        return "system"; // Default value
    }
}
