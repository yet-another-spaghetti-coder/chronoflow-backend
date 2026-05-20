package nus.edu.u.task.rpc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.shared.rpc.task.TaskDTO;
import nus.edu.u.shared.rpc.task.TaskRpcService;
import nus.edu.u.task.domain.dataobject.task.TaskDO;
import nus.edu.u.task.domain.dataobject.task.TaskLogDO;
import nus.edu.u.task.enums.TaskStatusEnum;
import nus.edu.u.task.mapper.TaskLogMapper;
import nus.edu.u.task.mapper.TaskMapper;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.annotation.Transactional;

/**
 * RPC implementation exposing task data to other bounded contexts.
 *
 * <p>Provides lookup utilities for tasks grouped by events as well as pending-task checks that are
 * required by the event service to validate group membership operations.
 */
@DubboService
@Slf4j
@RequiredArgsConstructor
public class TaskRpcServiceImpl implements TaskRpcService {

    private final TaskMapper taskMapper;
    private final TaskLogMapper taskLogMapper;

    @Override
    @Transactional
    public Map<Long, List<TaskDTO>> getTasksByEventIds(Collection<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Map.of();
        }

        List<TaskDO> tasks =
                taskMapper.selectList(
                        Wrappers.<TaskDO>lambdaQuery().in(TaskDO::getEventId, eventIds));
        if (tasks == null || tasks.isEmpty()) {
            return Map.of();
        }

        return tasks.stream()
                .filter(Objects::nonNull)
                .filter(task -> task.getEventId() != null)
                .collect(
                        Collectors.groupingBy(
                                TaskDO::getEventId,
                                LinkedHashMap::new,
                                Collectors.mapping(this::toTaskDTO, Collectors.toList())));
    }

    @Override
    public boolean hasPendingTasks(Long eventId, Long userId) {
        if (eventId == null || userId == null) {
            return false;
        }

        Long pendingCount =
                taskMapper.selectCount(
                        Wrappers.<TaskDO>lambdaQuery()
                                .eq(TaskDO::getEventId, eventId)
                                .eq(TaskDO::getUserId, userId)
                                .and(
                                        wrapper ->
                                                wrapper.ne(
                                                                TaskDO::getStatus,
                                                                TaskStatusEnum.COMPLETED
                                                                        .getStatus())
                                                        .or()
                                                        .isNull(TaskDO::getStatus)));

        return pendingCount != null && pendingCount > 0;
    }

    @Override
    public void deleteTasksByEventId(Long eventId) {
        if (eventId == null) {
            return;
        }

        List<TaskDO> tasks =
                taskMapper.selectList(
                        Wrappers.<TaskDO>lambdaQuery().eq(TaskDO::getEventId, eventId));
        if (tasks == null || tasks.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("No tasks to delete for event {}", eventId);
            }
            return;
        }

        Set<Long> taskIds =
                tasks.stream()
                        .map(TaskDO::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        if (!taskIds.isEmpty()) {
            int removedLogs =
                    taskLogMapper.delete(
                            Wrappers.<TaskLogDO>lambdaQuery().in(TaskLogDO::getTaskId, taskIds));
            if (log.isDebugEnabled()) {
                log.debug("Removed {} task logs for event {}", removedLogs, eventId);
            }
        }

        int removedTasks =
                taskMapper.delete(Wrappers.<TaskDO>lambdaQuery().eq(TaskDO::getEventId, eventId));
        log.info("Removed {} tasks for event {}", removedTasks, eventId);
    }

    private TaskDTO toTaskDTO(TaskDO task) {
        return TaskDTO.builder()
                .id(task.getId())
                .eventId(task.getEventId())
                .status(task.getStatus())
                .build();
    }

    @Override
    public TaskDTO getTaskDetail(Long taskId) {
        if (taskId == null) throw new IllegalArgumentException("taskId is required");

        TaskDO task = taskMapper.selectById(taskId);
        if (task == null) throw new IllegalArgumentException("Task not found: " + taskId);

        TaskDTO dto = new TaskDTO();
        dto.setId(task.getId());
        dto.setEventId(task.getEventId());
        dto.setName(task.getName());
        dto.setDescription(task.getDescription());
        dto.setStatus(task.getStatus());
        dto.setRemark(task.getRemark());
        dto.setStartTime(task.getStartTime());
        dto.setEndTime(task.getEndTime());
        return dto;
    }
}
