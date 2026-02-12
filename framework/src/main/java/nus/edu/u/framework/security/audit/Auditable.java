package nus.edu.u.framework.security.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for audit logging.
 *
 * <p>Usage example:
 * <pre>
 * &#64;Auditable(operation = "Assign Roles", type = AuditType.ADMIN_ACTION,
 *            targetType = "UserRole", targetId = "#reqVO.userId")
 * public void assignRoles(RoleAssignReqVO reqVO) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    /** Human-readable name of the operation, e.g. "Create Role". */
    String operation();

    /** Module name override. Defaults to auto-detection from package. */
    String module() default "";

    /** Audit event type. */
    AuditType type();

    /** Logical entity type, e.g. "Role", "Event", "Task". */
    String targetType() default "";

    /** SpEL expression evaluated against method arguments to extract target entity ID. */
    String targetId() default "";

    /** Field names to exclude from request body logging (e.g. passwords). */
    String[] excludeFields() default {};

    /** Whether to record the HTTP request body in the audit log. */
    boolean recordRequestBody() default true;
}
