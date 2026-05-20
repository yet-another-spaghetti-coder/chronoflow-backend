package nus.edu.u.common.jackson.desensitize;

import java.util.function.Function;

/** Desensitization strategies */
public enum DesensitizeType {
    /** User ID */
    USER_ID(s -> s.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2")),
    /** User name */
    CHINESE_NAME(s -> s.replaceAll("(\\S)\\S*", "$1**")),
    /** ID Card */
    ID_CARD(s -> s.replaceAll("(\\d{4})\\d{10}(\\w{4})", "$1**********$2")),
    /** Phone number */
    PHONE(s -> s.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2")),
    /** Address */
    ADDRESS(s -> s.replaceAll("(\\S{3})\\S{2}", "$1****")),
    /** Email */
    EMAIL(s -> s.replaceAll("(\\w?)(\\w+)(@\\w+\\.[a-z]+(\\.[a-z]+)?)", "$1****$3")),
    /** Password */
    PASSWORD(s -> "******"),
    /** Bank card */
    BANK_CARD(s -> s.replaceAll("(\\d{6})\\d{8,12}(\\d{4})", "$1******$2"));

    private final Function<String, String> desensitizer;

    DesensitizeType(Function<String, String> desensitizer) {
        this.desensitizer = desensitizer;
    }

    public String desensitize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return desensitizer.apply(value);
    }
}
