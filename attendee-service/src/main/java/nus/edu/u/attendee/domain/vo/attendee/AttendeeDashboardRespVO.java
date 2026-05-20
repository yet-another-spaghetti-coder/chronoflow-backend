package nus.edu.u.attendee.domain.vo.attendee;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendeeDashboardRespVO {
    private AttendeeSummaryVO summary;
    private PageVO<AttendeeSimpleVO> attendees;
}
