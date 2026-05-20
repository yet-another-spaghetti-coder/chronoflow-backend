package nus.edu.u.task.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import nus.edu.u.shared.rpc.notification.dto.common.NotificationRequestDTO;
import nus.edu.u.shared.rpc.notification.dto.task.NewTaskAssignmentDTO;
import nus.edu.u.task.mapper.notification.TaskNotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskNotificationPublisherTest {

    @Mock private NotificationPublisher notificationPublisher;

    @InjectMocks private TaskNotificationPublisher taskNotificationPublisher;

    private NewTaskAssignmentDTO request;

    @BeforeEach
    void setUp() {
        request =
                NewTaskAssignmentDTO.builder()
                        .taskId("task-100")
                        .eventId("event-200")
                        .assigneeUserId("user-300")
                        .assigneeEmail("assignee@example.com")
                        .assignerName("Alice Manager")
                        .taskName("Prepare slides")
                        .eventName("Annual Summit")
                        .description("Work with team to prepare the keynote slides.")
                        .build();
    }

    @Test
    void notifyNewTaskToAssigneePushDelegatesToNotificationPublisher() {
        when(notificationPublisher.publish(any(NotificationRequestDTO.class)))
                .thenReturn("push-id");
        NotificationRequestDTO expected =
                TaskNotificationMapper.newTaskAssignmentToPushNotification(request);

        String result = taskNotificationPublisher.notifyNewTaskToAssigneePush(request);

        assertThat(result).isEqualTo("push-id");
        ArgumentCaptor<NotificationRequestDTO> captor =
                ArgumentCaptor.forClass(NotificationRequestDTO.class);
        verify(notificationPublisher).publish(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void notifyNewTaskToAssigneeEmailDelegatesToNotificationPublisher() {
        when(notificationPublisher.publish(any(NotificationRequestDTO.class)))
                .thenReturn("email-id");
        NotificationRequestDTO expected =
                TaskNotificationMapper.newTaskAssignmentToEmailNotification(request);

        String result = taskNotificationPublisher.notifyNewTaskToAssigneeEmail(request);

        assertThat(result).isEqualTo("email-id");
        ArgumentCaptor<NotificationRequestDTO> captor =
                ArgumentCaptor.forClass(NotificationRequestDTO.class);
        verify(notificationPublisher).publish(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void notifyNewTaskToAssigneeWsDelegatesToNotificationPublisher() {
        when(notificationPublisher.publish(any(NotificationRequestDTO.class))).thenReturn("ws-id");
        NotificationRequestDTO expected =
                TaskNotificationMapper.newTaskAssignmentToWsNotification(request);

        String result = taskNotificationPublisher.notifyNewTaskToAssigneeWs(request);

        assertThat(result).isEqualTo("ws-id");
        ArgumentCaptor<NotificationRequestDTO> captor =
                ArgumentCaptor.forClass(NotificationRequestDTO.class);
        verify(notificationPublisher).publish(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(expected);
    }
}
