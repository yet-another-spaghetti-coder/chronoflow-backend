package nus.edu.u.repositories.common;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import nus.edu.u.domain.dataObject.common.NotificationEventDO;
import nus.edu.u.enums.common.NotificationEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationEventRepository extends JpaRepository<NotificationEventDO, String> {

    // ---------- Inbox listing (newest first) ----------
    Page<NotificationEventDO> findByRecipientUserIdOrderByCreatedAtDesc(
            String recipientUserId, Pageable pageable);

    // For "since" timeline pagination (fetch newer than a time)
    List<NotificationEventDO> findByRecipientUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
            String recipientUserId, LocalDateTime since);

    // Unread inbox (optional)
    Page<NotificationEventDO> findByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(
            String recipientUserId, Pageable pageable);

    // ---------- Single notification access (security check) ----------
    Optional<NotificationEventDO> findByIdAndRecipientUserId(String id, String recipientUserId);

    // ---------- Badge / counts ----------
    long countByRecipientUserIdAndReadFalse(String recipientUserId);

    // ---------- Optional filters ----------
    Page<NotificationEventDO> findByRecipientUserIdAndTypeOrderByCreatedAtDesc(
            String recipientUserId, NotificationEventType type, Pageable pageable);

    // ---------- Mark read (atomic update) ----------
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
        update NotificationEventDO n
           set n.read = true,
               n.readAt = :readAt
         where n.id = :id
           and n.recipientUserId = :recipientUserId
           and n.read = false
        """)
    int markRead(
            @Param("id") String id,
            @Param("recipientUserId") String recipientUserId,
            @Param("readAt") LocalDateTime readAt);
}
