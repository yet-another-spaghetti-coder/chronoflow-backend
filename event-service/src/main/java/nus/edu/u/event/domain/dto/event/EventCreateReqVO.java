package nus.edu.u.event.domain.dto.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Schema(description = "Event Create Request VO")
@Data
public class EventCreateReqVO {

    @NotBlank(message = "eventName cannot be empty")
    @JsonProperty("name")
    @Schema(description = "Event Name", requiredMode = Schema.RequiredMode.REQUIRED, example = "Event Name")
    private String eventName;

    @Schema(description = "Event Description", example = "Event Description")
    private String description;

    private Long organizerId;

    private List<Long> participantUserIds;

    @NotNull(message = "startTime cannot be empty")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime startTime;

    private String location;

    @NotNull(message = "endTime cannot be empty")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime endTime;

    private Integer status;

    @JsonProperty("remark")
    private String remarks;
}
