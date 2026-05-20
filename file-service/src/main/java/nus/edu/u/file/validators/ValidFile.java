package nus.edu.u.file.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = FileValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidFile {
    String message() default "Invalid file";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    long maxSizeMB() default 10;

    String[] allowedTypes() default {"image/jpeg", "image/png", "application/pdf"};
}
