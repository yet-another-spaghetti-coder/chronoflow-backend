package nus.edu.u.attendee.controller;

import static nus.edu.u.common.constant.PermissionConstants.CREATE_MEMBER;
import static nus.edu.u.common.core.domain.CommonResult.success;
import static nus.edu.u.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static nus.edu.u.common.utils.exception.ServiceExceptionUtil.exception;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.attendee.domain.vo.attendee.AttendeeDashboardRespVO;
import nus.edu.u.attendee.domain.vo.attendee.AttendeeInfoRespVO;
import nus.edu.u.attendee.domain.vo.attendee.AttendeeQrCodeRespVO;
import nus.edu.u.attendee.domain.vo.attendee.AttendeeReqVO;
import nus.edu.u.attendee.domain.vo.checkin.CheckInReqVO;
import nus.edu.u.attendee.domain.vo.checkin.CheckInRespVO;
import nus.edu.u.attendee.domain.vo.checkin.GenerateQrCodesReqVO;
import nus.edu.u.attendee.domain.vo.checkin.GenerateQrCodesRespVO;
import nus.edu.u.attendee.service.AttendeeService;
import nus.edu.u.attendee.service.excel.ExcelService;
import nus.edu.u.common.core.domain.CommonResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/attendees")
@Validated
@Slf4j
public class AttendeeController {

    @Resource private AttendeeService attendeeService;

    @Resource private ExcelService excelService;

    @GetMapping("/dashboard/{eventId}")
    public CommonResult<AttendeeDashboardRespVO> dashboard(
            @PathVariable("eventId") @NotNull Long eventId,
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "pageSize", defaultValue = "20") Integer pageSize) {
        return success(attendeeService.getDashboard(eventId, page, pageSize));
    }

    @GetMapping("list/{eventId}")
    public CommonResult<List<AttendeeQrCodeRespVO>> list(
            @PathVariable("eventId") @NotNull Long eventId) {
        return success(attendeeService.list(eventId));
    }

    @GetMapping("/{attendeeId}")
    public CommonResult<AttendeeQrCodeRespVO> get(
            @PathVariable("attendeeId") @NotNull Long attendeeId) {
        return success(attendeeService.get(attendeeId));
    }

    @DeleteMapping("/{attendeeId}")
    public CommonResult<Boolean> delete(@PathVariable("attendeeId") @NotNull Long attendeeId) {
        attendeeService.delete(attendeeId);
        return success(true);
    }

    @PatchMapping("/{attendeeId}")
    public CommonResult<AttendeeQrCodeRespVO> update(
            @PathVariable("attendeeId") @NotNull Long attendeeId,
            @RequestBody @Valid AttendeeReqVO reqVO) {
        return success(attendeeService.update(attendeeId, reqVO));
    }

    /** Generate batch QR codes */
    @SaCheckPermission(CREATE_MEMBER)
    @PostMapping
    public CommonResult<GenerateQrCodesRespVO> generateQrCodes(
            @RequestBody @Valid GenerateQrCodesReqVO reqVO) {
        log.info(
                "Generating QR codes for event {} with {} attendees",
                reqVO.getEventId(),
                reqVO.getAttendees().size());
        GenerateQrCodesRespVO response = attendeeService.generateQrCodesForAttendees(reqVO);
        return success(response);
    }

    @SaCheckPermission(CREATE_MEMBER)
    @PostMapping("/bulk/{eventId}")
    public CommonResult<GenerateQrCodesRespVO> generateQrCodeByExcel(
            @PathVariable("eventId") @NotNull Long eventId,
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw exception(BAD_REQUEST);
        }
        List<AttendeeReqVO> attendees = excelService.importAttendees(file);
        GenerateQrCodesReqVO reqVO = new GenerateQrCodesReqVO();
        reqVO.setAttendees(attendees);
        reqVO.setEventId(eventId);
        GenerateQrCodesRespVO response = attendeeService.generateQrCodesForAttendees(reqVO);
        return success(response);
    }

    @SaIgnore
    @GetMapping("/scan")
    public CommonResult<AttendeeInfoRespVO> attendeePreview(@RequestParam String token) {
        log.info(
                "Attendee preview request with token: {}",
                token.substring(0, Math.min(8, token.length())) + "...");

        AttendeeInfoRespVO info = attendeeService.getAttendeeInfo(token);
        info.setMessage("please show this QR code to the on-site staff for scanning");
        return success(info);
    }

    @PostMapping("/staff-scan")
    public CommonResult<CheckInRespVO> checkIn(@RequestBody @Valid CheckInReqVO reqVO) {
        log.info(
                "Check-in request with token: {}",
                reqVO.getToken().substring(0, Math.min(8, reqVO.getToken().length())) + "...");
        CheckInRespVO response = attendeeService.checkIn(reqVO.getToken());
        return success(response);
    }
}
