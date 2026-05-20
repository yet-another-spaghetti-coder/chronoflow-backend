package nus.edu.u.attendee.domain.vo.attendee;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendeeSummaryVO {
    private Long checkedIn;
    private Long nonCheckedIn;
}
