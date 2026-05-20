package nus.edu.u.attendee.service;

import java.util.List;
import nus.edu.u.attendee.domain.vo.attendee.AttendeeDashboardRespVO;
import nus.edu.u.attendee.domain.vo.attendee.AttendeeInfoRespVO;
import nus.edu.u.attendee.domain.vo.attendee.AttendeeQrCodeRespVO;
import nus.edu.u.attendee.domain.vo.attendee.AttendeeReqVO;
import nus.edu.u.attendee.domain.vo.checkin.CheckInRespVO;
import nus.edu.u.attendee.domain.vo.checkin.GenerateQrCodesReqVO;
import nus.edu.u.attendee.domain.vo.checkin.GenerateQrCodesRespVO;

public interface AttendeeService {
    /**
     * Perform check-in with token
     *
     * @param token Check-in token
     * @return Check-in result
     */
    CheckInRespVO checkIn(String token);

    /**
     * Generate check-in tokens and QR codes for multiple attendees Returns QR code data that can be
     * used by email service
     *
     * @param reqVO Request with eventId and userIds
     * @return QR codes for all attendees
     */
    GenerateQrCodesRespVO generateQrCodesForAttendees(GenerateQrCodesReqVO reqVO);

    /**
     * Get check-in token for a specific attendee
     *
     * @param eventId Event ID
     * @param email Attendee email
     * @return Check-in token
     */
    String getCheckInToken(Long eventId, String email);

    /**
     * List attendee info
     *
     * @param eventId Event id
     * @return Attendee info with qr code
     */
    List<AttendeeQrCodeRespVO> list(Long eventId);

    /**
     * Query attendee info by id
     *
     * @return One attendee info
     */
    AttendeeQrCodeRespVO get(Long attendeeId);

    /**
     * @param attendeeId attendee id
     */
    void delete(Long attendeeId);

    /**
     * Update attendee info
     *
     * @param attendeeId attendee id
     * @param reqVO attendee info
     * @return attendee info with qr code
     */
    AttendeeQrCodeRespVO update(Long attendeeId, AttendeeReqVO reqVO);

    AttendeeInfoRespVO getAttendeeInfo(String token);

    AttendeeDashboardRespVO getDashboard(Long eventId, Integer page, Integer pageSize);
}
