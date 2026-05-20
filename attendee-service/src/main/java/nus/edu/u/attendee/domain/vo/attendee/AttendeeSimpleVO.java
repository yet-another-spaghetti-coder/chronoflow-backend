package nus.edu.u.attendee.domain.vo.attendee;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendeeSimpleVO {
    private String name;
    private String email;
    private String mobile;
    private String createTime; // keep as String to avoid timezone headaches in webview
}
