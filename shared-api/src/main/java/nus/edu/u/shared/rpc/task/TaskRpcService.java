package nus.edu.u.shared.rpc.task;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface TaskRpcService {

    Map<Long, List<TaskDTO>> getTasksByEventIds(Collection<Long> eventIds);

    /**
     * Check whether a user still has unfinished tasks within the event.
     *
     * @param eventId event identifier
     * @param userId user identifier
     * @return true if the user has at least one task that is not completed
     */
    boolean hasPendingTasks(Long eventId, Long userId);

    /**
     * Remove all tasks associated with the specified event.
     *
     * @param eventId event identifier
     */
    void deleteTasksByEventId(Long eventId);

    TaskDTO getTaskDetail(Long taskId);
}
