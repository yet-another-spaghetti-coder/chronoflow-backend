package nus.edu.u.attendee.domain.vo.attendee;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageVO<T> {
    private List<T> items;
    private Integer page;
    private Integer pageSize;
    private Long total;
}
