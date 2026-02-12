package nus.edu.u.framework.security.audit;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Audit log event types aligned with STRIDE threat model.
 */
@Getter
@AllArgsConstructor
public enum AuditType {

    /** Authentication / authorization events (STRIDE: Spoofing). */
    SECURITY(1),

    /** Privilege-sensitive operations such as role/permission management (STRIDE: Elevation of Privilege). */
    ADMIN_ACTION(2),

    /** Entity create / update / delete operations (STRIDE: Tampering, Repudiation). */
    DATA_CHANGE(3),

    /** Gateway-level request logging (STRIDE: Repudiation). */
    API_ACCESS(4);

    private final int value;
}
