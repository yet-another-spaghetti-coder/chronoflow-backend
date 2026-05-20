package nus.edu.u.domain.dto.common;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDetailRespDTO {

    private NotificationEventDTO notificationEvent;
    private ActorDTO actor;
    private TaskResolvedDTO task;
    private EventResolvedDTO event;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationEventDTO {
        private String id;
        private String type;
        private LocalDateTime createdAt;
        private boolean read;
        private LocalDateTime readAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActorDTO {
        private String id;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskResolvedDTO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long eventId;

        private String name;
        private String description;
        private String remark;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventResolvedDTO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;

        private String name;
    }
}
