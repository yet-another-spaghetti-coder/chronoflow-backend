package nus.edu.u.user.controller.auth;

import static nus.edu.u.common.core.domain.CommonResult.success;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.common.core.domain.CommonResult;
import nus.edu.u.user.domain.vo.permission.PermissionReqVO;
import nus.edu.u.user.domain.vo.permission.PermissionRespVO;
import nus.edu.u.user.service.permission.PermissionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author Lu Shuwen
 * @date 2025-09-29
 */
@Tag(name = "Permission Controller")
@RestController
@RequestMapping("/users/permissions")
@Validated
@Slf4j
public class PermissionController {

    @Resource private PermissionService permissionService;

    @SaCheckRole(
            value = {"ORGANIZER", "ADMIN"},
            mode = SaMode.OR)
    @GetMapping
    @Operation(summary = "List all permissions")
    public CommonResult<List<PermissionRespVO>> list() {
        return success(permissionService.listPermissions());
    }

    @SaCheckRole("ADMIN")
    @PostMapping
    @Operation(summary = "Create a permission")
    public CommonResult<Long> create(@RequestBody @Valid PermissionReqVO reqVO) {
        return success(permissionService.createPermission(reqVO));
    }

    @SaCheckRole(
            value = {"ORGANIZER", "ADMIN"},
            mode = SaMode.OR)
    @GetMapping("/{id}")
    @Operation(summary = "Get a permission by id")
    public CommonResult<PermissionRespVO> getPermission(@PathVariable Long id) {
        return success(permissionService.getPermission(id));
    }

    @SaCheckRole("ADMIN")
    @PatchMapping("/{id}")
    @Operation(summary = "Update a permission")
    public CommonResult<PermissionRespVO> update(
            @PathVariable Long id, @RequestBody @Valid PermissionReqVO reqVO) {
        return success(permissionService.updatePermission(id, reqVO));
    }

    @SaCheckRole("ADMIN")
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a permission")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        return success(permissionService.deletePermission(id));
    }
}
