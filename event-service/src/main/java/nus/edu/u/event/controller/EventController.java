package nus.edu.u.event.controller;

import static nus.edu.u.common.constant.PermissionConstants.*;
import static nus.edu.u.common.core.domain.CommonResult.success;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import nus.edu.u.common.core.domain.CommonResult;
import nus.edu.u.event.domain.dto.event.EventCreateReqVO;
import nus.edu.u.event.domain.dto.event.EventGroupRespVO;
import nus.edu.u.event.domain.dto.event.EventRespVO;
import nus.edu.u.event.domain.dto.event.EventUpdateReqVO;
import nus.edu.u.event.domain.dto.event.UpdateEventRespVO;
import nus.edu.u.event.service.EventApplicationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Event Controller")
@RestController
@RequestMapping("/events")
@Validated
@RequiredArgsConstructor
public class EventController {

    private final EventApplicationService eventApplicationService;

    @SaCheckPermission(CREATE_EVENT)
    @PostMapping
    @Operation(summary = "Create an event")
    public CommonResult<EventRespVO> create(@Valid @RequestBody EventCreateReqVO request) {
        Long organizerId = StpUtil.getLoginIdAsLong();
        request.setOrganizerId(organizerId);
        EventRespVO resp = eventApplicationService.createEvent(request);
        return success(resp);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an event by id")
    public CommonResult<EventRespVO> getById(@PathVariable Long id) {
        return success(eventApplicationService.getEvent(id));
    }

    @GetMapping
    @Operation(summary = "Get all events")
    public CommonResult<List<EventRespVO>> list() {
        return success(eventApplicationService.list());
    }

    @SaCheckPermission(UPDATE_EVENT)
    @PatchMapping("/{id}")
    @Operation(summary = "Update an event")
    public CommonResult<UpdateEventRespVO> update(
            @PathVariable Long id, @Valid @RequestBody EventUpdateReqVO request) {
        UpdateEventRespVO respVO = eventApplicationService.updateEvent(id, request);
        return success(respVO);
    }

    @SaCheckPermission(DELETE_EVENT)
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an event")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        return success(eventApplicationService.deleteEvent(id));
    }

    @SaCheckPermission(UPDATE_EVENT)
    @PatchMapping("/{id}/restore")
    @Operation(summary = "Restore an event")
    public CommonResult<Boolean> restore(@PathVariable Long id) {
        return success(eventApplicationService.restoreEvent(id));
    }

    @SaCheckPermission(ASSIGN_TASK)
    @GetMapping("/{id}/assignable-groups")
    @Operation(summary = "Get assignable groups for an event")
    public CommonResult<List<EventGroupRespVO>> assignableGroups(@PathVariable("id") Long eventId) {
        return success(eventApplicationService.findAssignableGroups(eventId));
    }
}
