package nus.edu.u.framework.mybatis;

import static nus.edu.u.common.constant.Constants.SESSION_TENANT_ID;
import static nus.edu.u.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static nus.edu.u.common.utils.exception.ServiceExceptionUtil.exception;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * @author Lu Shuwen
 * @date 2025-09-12
 */
@AutoConfiguration
public class MybatisPlusConfig {

    /**
     * Thread-local flag to bypass tenant filtering for specific operations
     * like Firebase login where we need to look up a user before authentication.
     */
    private static final ThreadLocal<Boolean> IGNORE_TENANT = ThreadLocal.withInitial(() -> false);

    /**
     * Execute a runnable with tenant filtering bypassed.
     * Use this for operations that need to query across tenants before user is authenticated.
     */
    public static void executeWithoutTenantFilter(Runnable runnable) {
        try {
            IGNORE_TENANT.set(true);
            runnable.run();
        } finally {
            IGNORE_TENANT.remove();
        }
    }

    /**
     * Execute a supplier with tenant filtering bypassed and return the result.
     * Use this for operations that need to query across tenants before user is authenticated.
     */
    public static <T> T executeWithoutTenantFilter(java.util.function.Supplier<T> supplier) {
        try {
            IGNORE_TENANT.set(true);
            return supplier.get();
        } finally {
            IGNORE_TENANT.remove();
        }
    }

    /**
     * Check if tenant filtering is currently bypassed.
     * Used by MetaObjectHandler to skip auto-filling tenant_id.
     */
    public static boolean isTenantFilterBypassed() {
        return Boolean.TRUE.equals(IGNORE_TENANT.get());
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // ✅ Muti-tenant
        interceptor.addInnerInterceptor(
                new TenantLineInnerInterceptor(
                        new TenantLineHandler() {
                            @Override
                            public Expression getTenantId() {
                                // If tenant filtering is bypassed, return a dummy value
                                // (ignoreTable will return true anyway)
                                if (Boolean.TRUE.equals(IGNORE_TENANT.get())) {
                                    return new LongValue(0L);
                                }
                                Long tenantId = getCurrentTenantId();
                                return new LongValue(tenantId);
                            }

                            @Override
                            public String getTenantIdColumn() {
                                return "tenant_id"; // Tenant field name in the database table
                            }

                            @Override
                            public boolean ignoreTable(String tableName) {
                                // If tenant filtering is bypassed, ignore all tables
                                if (Boolean.TRUE.equals(IGNORE_TENANT.get())) {
                                    return true;
                                }
                                // Return true to ignore the table and not join the tenant
                                // conditions.
                                return "sys_dict_data".equals(tableName)
                                        || "sys_dict_type".equals(tableName)
                                        || "sys_tenant".equals(tableName)
                                        || "sys_permission".equals(tableName);
                            }
                        }));

        // ✅ Pagination
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());

        // ✅ Avoid update or delete all data in table
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }

    @Bean
    public MybatisMetaObjectHandler mybatisMetaObjectHandler() {
        return new MybatisMetaObjectHandler();
    }

    public static Long getCurrentTenantId() {
        try {
            Object tenantIdObject = StpUtil.getSession().get(SESSION_TENANT_ID);
            Long tenantId = Long.parseLong(tenantIdObject.toString());
            if (ObjectUtil.isNotNull(tenantId)) {
                return tenantId;
            }
        } catch (Exception e) {
            throw exception(UNAUTHORIZED);
        }
        return 1L;
    }
}
