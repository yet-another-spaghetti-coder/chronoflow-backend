package nus.edu.u.shared.rpc.notification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationObjectType {
    TASK("task"),
    EVENT("event"),
    COMMENT("comment");

    private final String code;

    @Override
    public String toString() {
        return code;
    }

    public static NotificationObjectType fromCode(String code) {
        for (var t : values()) {
            if (t.code.equalsIgnoreCase(code)) return t;
        }
        throw new IllegalArgumentException("Unknown objectType: " + code);
    }
}
