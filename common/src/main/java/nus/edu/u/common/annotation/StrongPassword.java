package nus.edu.u.common.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import nus.edu.u.common.validation.StrongPasswordValidator;

/**
 * Marks a {@code String} field as a user password that must satisfy ChronoFlow's password policy
 * (SR-A-07): length 12–128, at least one each of upper-case, lower-case, digit, and symbol, and not
 * present in the bundled breached-password list.
 *
 * <p>Cross-field rules (e.g. "must not equal the user's email/username") cannot be expressed at the
 * field level — invoke {@code PasswordPolicyService.assertNotIdentity} at the service layer.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
public @interface StrongPassword {

    String message() default
            "Password must be 12–128 characters and include upper-case, lower-case, digit, and"
                    + " symbol; common or breached passwords are not allowed";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int min() default 12;

    int max() default 128;

    boolean requireUpper() default true;

    boolean requireLower() default true;

    boolean requireDigit() default true;

    boolean requireSymbol() default true;

    boolean rejectCommon() default true;
}
