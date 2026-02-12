package nus.edu.u.attendee.service;

import static nus.edu.u.common.enums.ErrorCodeConstants.*;
import static nus.edu.u.common.utils.exception.ServiceExceptionUtil.exception;
import static nus.edu.u.framework.mybatis.MybatisPlusConfig.getCurrentTenantId;

import cn.hutool.core.util.ObjectUtil;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.attendee.domain.dataobject.EventAttendeeDO;
import nus.edu.u.framework.security.audit.AuditType;
import nus.edu.u.framework.security.audit.Auditable;
import nus.edu.u.attendee.domain.vo.attendee.AttendeeInfoRespVO;
import nus.edu.u.attendee.domain.vo.attendee.AttendeeQrCodeRespVO;
import nus.edu.u.attendee.domain.vo.attendee.AttendeeReqVO;
import nus.edu.u.attendee.domain.vo.checkin.CheckInRespVO;
import nus.edu.u.attendee.domain.vo.checkin.GenerateQrCodesReqVO;
import nus.edu.u.attendee.domain.vo.checkin.GenerateQrCodesRespVO;
import nus.edu.u.attendee.domain.vo.qrcode.QrCodeRespVO;
import nus.edu.u.attendee.mapper.EventAttendeeMapper;
import nus.edu.u.attendee.publisher.AttendeeNotificationPublisher;
import nus.edu.u.attendee.service.qrcode.QrCodeService;
import nus.edu.u.common.enums.EventStatusEnum;
import nus.edu.u.shared.rpc.events.EventRespDTO;
import nus.edu.u.shared.rpc.events.EventRpcService;
import nus.edu.u.shared.rpc.notification.dto.Attendee.AttendeeInviteReqDTO;
import nus.edu.u.shared.rpc.user.TenantDTO;
import nus.edu.u.shared.rpc.user.UserRpcService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AttendeeServiceImpl implements AttendeeService {

    private final EventAttendeeMapper attendeeMapper;

    @DubboReference(check = false)
    private EventRpcService eventRpcService;

    private final QrCodeService qrCodeService;

    private final AttendeeNotificationPublisher attendeeNotificationPublisher;

    @DubboReference private UserRpcService userRpcService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    public List<AttendeeQrCodeRespVO> list(Long eventId) {
        List<EventAttendeeDO> list = attendeeMapper.selectByEventId(eventId);
        if (ObjectUtil.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(
                        attendee ->
                                AttendeeQrCodeRespVO.builder()
                                        .id(attendee.getId())
                                        .attendeeEmail(attendee.getAttendeeEmail())
                                        .attendeeMobile(attendee.getAttendeeMobile())
                                        .attendeeName(attendee.getAttendeeName())
                                        .checkInToken(attendee.getCheckInToken())
                                        .checkInStatus(attendee.getCheckInStatus())
                                        .build())
                .toList();
    }

    @Override
    public AttendeeQrCodeRespVO get(Long attendeeId) {
        EventAttendeeDO attendee = attendeeMapper.selectById(attendeeId);
        if (ObjectUtil.isNull(attendee)) {
            return null;
        }
        return AttendeeQrCodeRespVO.builder()
                .id(attendee.getId())
                .attendeeEmail(attendee.getAttendeeEmail())
                .attendeeMobile(attendee.getAttendeeMobile())
                .attendeeName(attendee.getAttendeeName())
                .checkInToken(attendee.getCheckInToken())
                .checkInStatus(attendee.getCheckInStatus())
                .build();
    }

    @Override
    @Auditable(operation = "Delete Attendee", type = AuditType.DATA_CHANGE,
               targetType = "Attendee", targetId = "#attendeeId")
    public void delete(Long attendeeId) {
        EventAttendeeDO attendee = attendeeMapper.selectById(attendeeId);
        if (ObjectUtil.isEmpty(attendee)) {
            throw exception(ATTENDEE_NOT_EXIST);
        }
        attendeeMapper.deleteById(attendeeId);
    }

    @Override
    @Auditable(operation = "Update Attendee", type = AuditType.DATA_CHANGE,
               targetType = "Attendee", targetId = "#attendeeId")
    public AttendeeQrCodeRespVO update(Long attendeeId, AttendeeReqVO reqVO) {
        EventAttendeeDO attendee = attendeeMapper.selectById(attendeeId);
        if (ObjectUtil.isEmpty(attendee)) {
            throw exception(ATTENDEE_NOT_EXIST);
        }
        if (ObjectUtil.equals(attendee.getCheckInStatus(), 1)) {
            throw exception(UPDATE_ATTENDEE_FAILED);
        }
        EventRespDTO event = eventRpcService.getEvent(attendee.getEventId());
        System.out.println("get event now" + event);
        if (ObjectUtil.isEmpty(event)) {
            throw exception(EVENT_NOT_FOUND);
        }
        attendee.setAttendeeEmail(reqVO.getEmail());
        attendee.setAttendeeMobile(reqVO.getMobile());
        attendee.setAttendeeName(reqVO.getName());
        boolean isSuccess = attendeeMapper.updateById(attendee) > 0;
        if (!isSuccess) {
            throw exception(UPDATE_ATTENDEE_FAILED);
        }

        String token = attendee.getCheckInToken();
        if (ObjectUtil.isNull(attendee.getCheckInToken())) {
            token = UUID.randomUUID().toString();
            attendee.setCheckInToken(token);
            attendee.setQrCodeGeneratedTime(LocalDateTime.now());
            attendeeMapper.updateById(attendee);
            log.info(
                    "Generated new token for updating attendee: email={}",
                    attendee.getAttendeeEmail());
        }
        // Generate QR code
        QrCodeRespVO qrCode = qrCodeService.generateEventCheckInQrWithToken(token);
        String qrCodeUrl = baseUrl + "/system/attendee/scan?token=" + token;

        // Send email
        sendEmail(attendee, event, qrCode);

        return AttendeeQrCodeRespVO.builder()
                .id(attendee.getId())
                .attendeeEmail(attendee.getAttendeeEmail())
                .attendeeName(attendee.getAttendeeName())
                .attendeeMobile(attendee.getAttendeeMobile())
                .checkInToken(token)
                .qrCodeBase64(qrCode.getBase64Image())
                .qrCodeUrl(qrCodeUrl)
                .checkInStatus(attendee.getCheckInStatus())
                .build();
    }

    @Override
    @Transactional
    @Auditable(operation = "Check In", type = AuditType.DATA_CHANGE,
               targetType = "Attendee", targetId = "#token")
    public CheckInRespVO checkIn(String token) {
        // 1. Validate token
        EventAttendeeDO attendee = attendeeMapper.selectByToken(token);
        if (ObjectUtil.isNull(attendee)) {
            throw exception(INVALID_CHECKIN_TOKEN);
        }

        // 2. Check if already checked in
        if (ObjectUtil.equal(attendee.getCheckInStatus(), 1)) {
            throw exception(ALREADY_CHECKED_IN);
        }

        // 3. Validate event status and time
        EventRespDTO event = eventRpcService.getEvent(attendee.getEventId());
        if (ObjectUtil.isNull(event)) {
            throw exception(EVENT_NOT_FOUND);
        }

        if (!ObjectUtil.equal(event.getStatus(), EventStatusEnum.ACTIVE.getCode())) {
            throw exception(EVENT_NOT_ACTIVE);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime checkInStart = event.getStartTime().minusHours(2);
        LocalDateTime checkInEnd = event.getEndTime();

        if (now.isBefore(checkInStart)) {
            throw exception(CHECKIN_NOT_STARTED);
        }
        if (now.isAfter(checkInEnd)) {
            throw exception(CHECKIN_ENDED);
        }

        // 4. Update check-in status
        attendee.setCheckInStatus(1);
        attendee.setCheckInTime(now);
        attendeeMapper.updateById(attendee);

        log.info(
                "Attendee {} ({}) checked in for event {}",
                attendee.getAttendeeName(),
                attendee.getAttendeeEmail(),
                event.getName());

        return CheckInRespVO.builder()
                .eventId(event.getId())
                .eventName(event.getName())
                .userId(null) // Attendee 没有 userId
                .userName(attendee.getAttendeeName())
                .checkInTime(now)
                .success(true)
                .message("Check-in successful!")
                .build();
    }

    @Override
    @Transactional
    @Auditable(operation = "Generate QR Codes", type = AuditType.DATA_CHANGE,
               targetType = "Attendee", targetId = "#reqVO.eventId")
    public GenerateQrCodesRespVO generateQrCodesForAttendees(GenerateQrCodesReqVO reqVO) {
        Long eventId = reqVO.getEventId();
        List<AttendeeReqVO> attendeeInfos = reqVO.getAttendees();

        // 1. Validate event
        EventRespDTO event = eventRpcService.getEvent(eventId);
        if (ObjectUtil.isNull(event)) {
            throw exception(EVENT_NOT_FOUND);
        }

        // 2. Process each attendee
        List<AttendeeQrCodeRespVO> successList = new ArrayList<>();
        List<String> failedList = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (AttendeeReqVO info : attendeeInfos) {
            try {
                if (info.getEmail() == null || info.getEmail().isBlank()) {
                    failedList.add("email can't be empty");
                    continue;
                }

                EventAttendeeDO existing =
                        attendeeMapper.selectByEventAndEmail(eventId, info.getEmail());

                if (ObjectUtil.isNotNull(existing)) {
                    failedList.add(info.getEmail() + " - already exist in this event");
                    log.warn(
                            "Attendee already exists: email={}, eventId={}",
                            info.getEmail(),
                            eventId);
                    continue;
                }

                String token = UUID.randomUUID().toString();
                EventAttendeeDO attendee =
                        EventAttendeeDO.builder()
                                .eventId(eventId)
                                .attendeeEmail(info.getEmail())
                                .attendeeName(info.getName())
                                .attendeeMobile(info.getMobile())
                                .checkInToken(token)
                                .checkInStatus(0)
                                .qrCodeGeneratedTime(now)
                                .build();

                attendeeMapper.insert(attendee);

                QrCodeRespVO qrCode = qrCodeService.generateEventCheckInQrWithToken(token);
                String qrCodeUrl = baseUrl + "/system/attendee/scan?token=" + token;

                try {
                    sendEmail(attendee, event, qrCode);
                } catch (Exception e) {
                    log.error("Failed to send email to {}: {}", info.getEmail(), e.getMessage());
                }

                successList.add(
                        AttendeeQrCodeRespVO.builder()
                                .id(attendee.getId())
                                .attendeeEmail(attendee.getAttendeeEmail())
                                .attendeeName(attendee.getAttendeeName())
                                .attendeeMobile(attendee.getAttendeeMobile())
                                .checkInToken(token)
                                .qrCodeBase64(qrCode.getBase64Image())
                                .qrCodeUrl(qrCodeUrl)
                                .checkInStatus(0)
                                .build());

            } catch (Exception e) {
                log.error("Error processing attendee {}: {}", info.getEmail(), e.getMessage(), e);
                failedList.add(info.getEmail() + " - System error");
            }
        }

        log.info(
                "Event {}: {} succeeded, {} failed",
                eventId,
                successList.size(),
                failedList.size());

        if (!failedList.isEmpty()) {
            log.warn("Failed attendees: {}", failedList);
        }

        if (successList.isEmpty()) {
            String errorMsg = String.join("; ", failedList);
            throw exception(ATTENDEE_CREATION_FAILED, "Fail to create" + errorMsg);
        }

        return GenerateQrCodesRespVO.builder()
                .eventId(eventId)
                .eventName(event.getName())
                .totalCount(successList.size())
                .attendees(successList)
                .build();
    }

    @Override
    @Transactional
    public String getCheckInToken(Long eventId, String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }

        // Validate event exists
        EventRespDTO event = eventRpcService.getEvent(eventId);
        if (ObjectUtil.isNull(event)) {
            throw exception(EVENT_NOT_FOUND);
        }

        // Find attendee by email
        EventAttendeeDO attendee = attendeeMapper.selectByEventAndEmail(eventId, email);

        if (ObjectUtil.isNull(attendee)) {
            // Attendee doesn't exist yet - will be created when sending email
            log.info("Attendee not found for eventId={}, email={}", eventId, email);
            throw exception(EVENT_ATTENDEE_NOT_FOUND);
        }

        // Generate token if doesn't exist
        if (ObjectUtil.isNull(attendee.getCheckInToken())) {
            String token = UUID.randomUUID().toString();
            attendee.setCheckInToken(token);
            attendee.setQrCodeGeneratedTime(LocalDateTime.now());
            attendeeMapper.updateById(attendee);
            log.info("Generated token for attendee: email={}", email);
        }

        return attendee.getCheckInToken();
    }

    private void sendEmail(EventAttendeeDO attendee, EventRespDTO event, QrCodeRespVO qrCode) {
        byte[] qrCodeBytes = Base64.getDecoder().decode(qrCode.getBase64Image());
        Long currentTenantId = getCurrentTenantId();
        TenantDTO tenant = null;

        if (currentTenantId != null) {
            try {
                tenant = userRpcService.getTenantById(currentTenantId);
                if (tenant == null) {
                    log.warn("Tenant not found for tenantId: {}", currentTenantId);
                }
            } catch (Exception e) {
                log.error("Failed to get tenant info via RPC for tenantId: {}", currentTenantId, e);
                throw exception(ATTENDEE_CREATION_FAILED);
            }
        }

        AttendeeInviteReqDTO emailReq =
                AttendeeInviteReqDTO.builder()
                        .toEmail(attendee.getAttendeeEmail())
                        .attendeeMobile(attendee.getAttendeeMobile())
                        .attendeeName(attendee.getAttendeeName())
                        .qrCodeBytes(qrCodeBytes)
                        .qrCodeContentType(qrCode.getContentType())
                        .eventName(event.getName())
                        .eventDescription(event.getDescription())
                        .eventId(event.getId())
                        .eventLocation(event.getLocation())
                        .eventDate(event.getStartTime().toString())
                        .organizationName(ObjectUtil.isNotNull(tenant) ? tenant.getName() : "")
                        .build();

        attendeeNotificationPublisher.sendAttendeeInviteEmail(emailReq);
    }

    @Override
    public AttendeeInfoRespVO getAttendeeInfo(String token) {
        if (token == null || token.isBlank()) {
            throw exception(INVALID_CHECKIN_TOKEN);
        }

        EventAttendeeDO attendee = attendeeMapper.selectByToken(token);
        if (ObjectUtil.isNull(attendee)) {
            throw exception(INVALID_CHECKIN_TOKEN);
        }

        EventRespDTO event = eventRpcService.getEvent(attendee.getEventId());
        if (ObjectUtil.isNull(event)) {
            throw exception(EVENT_NOT_FOUND);
        }

        AttendeeInfoRespVO respVO =
                AttendeeInfoRespVO.builder()
                        .eventName(event.getName())
                        .attendeeName(attendee.getAttendeeName())
                        .attendeeEmail(attendee.getAttendeeEmail())
                        .checkInStatus(attendee.getCheckInStatus())
                        .checkInTime(attendee.getCheckInTime())
                        .build();

        log.info(
                "Attendee info retrieved: name={}, email={}, checkInStatus={}",
                attendee.getAttendeeName(),
                attendee.getAttendeeEmail(),
                attendee.getCheckInStatus());

        return respVO;
    }
}
