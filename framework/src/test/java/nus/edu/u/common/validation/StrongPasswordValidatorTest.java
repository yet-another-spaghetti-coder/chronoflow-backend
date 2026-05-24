package nus.edu.u.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder;
import nus.edu.u.common.annotation.StrongPassword;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StrongPasswordValidatorTest {

    @Mock private ConstraintValidatorContext ctx;
    @Mock private ConstraintViolationBuilder builder;

    private StrongPasswordValidator validator;

    @BeforeEach
    void setUp() {
        // The validator builds a custom message via the context on every failure path.
        // Stub the chain so the calls don't NPE.
        when(ctx.buildConstraintViolationWithTemplate(anyStringMessage())).thenReturn(builder);

        StrongPassword annotation =
                new StrongPassword() {
                    @Override
                    public Class<? extends java.lang.annotation.Annotation> annotationType() {
                        return StrongPassword.class;
                    }

                    @Override
                    public String message() {
                        return "default";
                    }

                    @Override
                    public Class<?>[] groups() {
                        return new Class[0];
                    }

                    @Override
                    public Class<? extends jakarta.validation.Payload>[] payload() {
                        return new Class[0];
                    }

                    @Override
                    public int min() {
                        return 12;
                    }

                    @Override
                    public int max() {
                        return 128;
                    }

                    @Override
                    public boolean requireUpper() {
                        return true;
                    }

                    @Override
                    public boolean requireLower() {
                        return true;
                    }

                    @Override
                    public boolean requireDigit() {
                        return true;
                    }

                    @Override
                    public boolean requireSymbol() {
                        return true;
                    }

                    @Override
                    public boolean rejectCommon() {
                        return true;
                    }
                };
        validator = new StrongPasswordValidator();
        validator.initialize(annotation);
    }

    private static String anyStringMessage() {
        return org.mockito.ArgumentMatchers.anyString();
    }

    // --- empty / null defers to @NotEmpty / @NotBlank ------------------------

    @Test
    void isValid_nullPassword_returnsTrue() {
        assertThat(validator.isValid(null, ctx)).isTrue();
    }

    @Test
    void isValid_emptyPassword_returnsTrue() {
        assertThat(validator.isValid("", ctx)).isTrue();
    }

    // --- length --------------------------------------------------------------

    @Test
    void isValid_tooShort_returnsFalse() {
        assertThat(validator.isValid("Ab1!cdef", ctx)).isFalse(); // 8 chars
    }

    @Test
    void isValid_tooLong_returnsFalse() {
        // 130 chars: 32 × "Ab1!" = 128, plus 2 more
        String tooLong = "Ab1!".repeat(32) + "Cd";
        assertThat(tooLong.length()).isEqualTo(130);
        assertThat(validator.isValid(tooLong, ctx)).isFalse();
    }

    // --- character class requirements ---------------------------------------

    @Test
    void isValid_missingUpper_returnsFalse() {
        assertThat(validator.isValid("alllowercaseonly", ctx)).isFalse();
    }

    @Test
    void isValid_missingLower_returnsFalse() {
        assertThat(validator.isValid("ALLUPPERCASE1!@", ctx)).isFalse();
    }

    @Test
    void isValid_missingDigit_returnsFalse() {
        assertThat(validator.isValid("NoDigitsHere!@#", ctx)).isFalse();
    }

    @Test
    void isValid_missingSymbol_returnsFalse() {
        assertThat(validator.isValid("NoSymbol12345", ctx)).isFalse();
    }

    // --- breach list --------------------------------------------------------

    @Test
    void isValid_breachedEntry_returnsFalse() {
        // "Password2026!" is in the bundled common-passwords.txt; passes every syntactic rule.
        assertThat(validator.isValid("Password2026!", ctx)).isFalse();
    }

    @Test
    void isValid_breachedEntryCaseInsensitive_returnsFalse() {
        // The validator lowercases before checking the breach list.
        assertThat(validator.isValid("WELCOME2026!", ctx)).isFalse();
    }

    // --- happy path ---------------------------------------------------------

    @Test
    void isValid_strongPassword_returnsTrue() {
        assertThat(validator.isValid("Zphyr-7Brave-Wolf!", ctx)).isTrue();
    }

    @Test
    void isValid_anotherStrongPassword_returnsTrue() {
        assertThat(validator.isValid("Correct$Horse9Battery", ctx)).isTrue();
    }

    // --- knob: rejectCommon=false ------------------------------------------

    @Test
    void isValid_breachedEntry_allowedWhenRejectCommonDisabled() {
        StrongPasswordValidator v = new StrongPasswordValidator();
        v.initialize(annotationWith(false));
        assertThat(v.isValid("Password2026!", mock(ConstraintValidatorContext.class))).isTrue();
    }

    private StrongPassword annotationWith(boolean rejectCommon) {
        return new StrongPassword() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return StrongPassword.class;
            }

            @Override
            public String message() {
                return "default";
            }

            @Override
            public Class<?>[] groups() {
                return new Class[0];
            }

            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public int min() {
                return 12;
            }

            @Override
            public int max() {
                return 128;
            }

            @Override
            public boolean requireUpper() {
                return true;
            }

            @Override
            public boolean requireLower() {
                return true;
            }

            @Override
            public boolean requireDigit() {
                return true;
            }

            @Override
            public boolean requireSymbol() {
                return true;
            }

            @Override
            public boolean rejectCommon() {
                return rejectCommon;
            }
        };
    }
}
