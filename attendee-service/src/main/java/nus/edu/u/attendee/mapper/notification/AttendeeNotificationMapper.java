package nus.edu.u.attendee.mapper.notification;

import java.util.*;
import nus.edu.u.shared.rpc.notification.dto.Attendee.AttendeeInviteReqDTO;
import nus.edu.u.shared.rpc.notification.dto.common.AttachmentDTO;
import nus.edu.u.shared.rpc.notification.dto.common.NotificationRequestDTO;
import nus.edu.u.shared.rpc.notification.enums.NotificationChannel;
import nus.edu.u.shared.rpc.notification.enums.NotificationEventType;

public class AttendeeNotificationMapper {

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public static NotificationRequestDTO attendeeInvitationToNotification(
            AttendeeInviteReqDTO req) {

        // These should NOT be null; fail fast with clear message
        String toEmail = Objects.requireNonNull(req.getToEmail(), "toEmail is null");
        Object eventIdObj = Objects.requireNonNull(req.getEventId(), "eventId is null");

        Map<String, Object> vars = new HashMap<>();
        vars.put("attendeeName", nz(req.getAttendeeName()));
        vars.put("attendeeMobile", nz(req.getAttendeeMobile()));
        vars.put("organizationName", nz(req.getOrganizationName()));
        vars.put("eventName", nz(req.getEventName()));
        vars.put(
                "eventDate",
                req.getEventDate() == null
                        ? ""
                        : req.getEventDate()); // keep as-is if it's not String
        vars.put("eventLocation", nz(req.getEventLocation()));
        vars.put("eventDescription", nz(req.getEventDescription()));

        List<AttachmentDTO> attachments =
                (req.getQrCodeBytes() != null)
                        ? List.of(
                                AttachmentDTO.builder()
                                        .filename("qrcode.png")
                                        .contentType(
                                                nz(req.getQrCodeContentType()).isEmpty()
                                                        ? "image/png"
                                                        : req.getQrCodeContentType())
                                        .bytes(req.getQrCodeBytes())
                                        .inline(true)
                                        .contentId("qr-code")
                                        .build())
                        : List.of();

        return NotificationRequestDTO.builder()
                .channel(NotificationChannel.EMAIL)
                .to(toEmail)
                .recipientKey("email:" + toEmail)
                .templateId("attendee-qr-invite")
                .variables(vars)
                .locale(Locale.ENGLISH)
                .attachments(attachments)
                .eventId("attendee-invite-" + eventIdObj + "-" + toEmail)
                .type(NotificationEventType.ATTENDEE_INVITE)
                .build();
    }
}
