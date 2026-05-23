package nus.edu.u.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import nus.edu.u.common.annotation.StrongPassword;

/** Validates the syntactic password-policy rules declared on {@link StrongPassword}. */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final String COMMON_PASSWORDS_RESOURCE = "/security/common-passwords.txt";

    /** Loaded once at class-load. Compared case-insensitively. */
    private static final Set<String> COMMON_PASSWORDS = loadCommonPasswords();

    private int min;
    private int max;
    private boolean requireUpper;
    private boolean requireLower;
    private boolean requireDigit;
    private boolean requireSymbol;
    private boolean rejectCommon;

    @Override
    public void initialize(StrongPassword a) {
        this.min = a.min();
        this.max = a.max();
        this.requireUpper = a.requireUpper();
        this.requireLower = a.requireLower();
        this.requireDigit = a.requireDigit();
        this.requireSymbol = a.requireSymbol();
        this.rejectCommon = a.rejectCommon();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null/empty handling: defer to @NotEmpty / @NotBlank — same convention as MobileValidator.
        if (value == null || value.isEmpty()) {
            return true;
        }
        if (value.length() < min || value.length() > max) {
            return rejectWith(
                    context,
                    "Password length must be between " + min + " and " + max + " characters");
        }

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (!Character.isWhitespace(c)) hasSymbol = true;
        }
        if (requireUpper && !hasUpper) {
            return rejectWith(context, "Password must contain at least one upper-case letter");
        }
        if (requireLower && !hasLower) {
            return rejectWith(context, "Password must contain at least one lower-case letter");
        }
        if (requireDigit && !hasDigit) {
            return rejectWith(context, "Password must contain at least one digit");
        }
        if (requireSymbol && !hasSymbol) {
            return rejectWith(
                    context, "Password must contain at least one symbol (e.g. ! @ # $ %)");
        }
        if (rejectCommon && COMMON_PASSWORDS.contains(value.toLowerCase())) {
            return rejectWith(
                    context,
                    "This password is too common or has appeared in known data breaches. Please"
                            + " choose a different one");
        }
        return true;
    }

    private static boolean rejectWith(ConstraintValidatorContext ctx, String msg) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(msg).addConstraintViolation();
        return false;
    }

    private static Set<String> loadCommonPasswords() {
        try (InputStream is =
                StrongPasswordValidator.class.getResourceAsStream(COMMON_PASSWORDS_RESOURCE)) {
            if (is == null) {
                return Set.of();
            }
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return r.lines()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                        .map(String::toLowerCase)
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException e) {
            return Set.of();
        }
    }
}
