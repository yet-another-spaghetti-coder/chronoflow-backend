package nus.edu.u.user.controller.auth;

import static nus.edu.u.common.constant.PermissionConstants.*;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.common.core.domain.CommonResult;
import nus.edu.u.user.domain.vo.role.RoleAssignReqVO;
import nus.edu.u.user.domain.vo.role.RoleReqVO;
import nus.edu.u.user.domain.vo.role.RoleRespVO;
import nus.edu.u.user.service.role.RoleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Role Controller
 *
 * @author Lu Shuwen
 * @date 2025-09-13
 */
@Tag(name = "Role Controller")
@RestController
@RequestMapping("/users/roles")
@Validated
@Slf4j
public class RoleController {

    @Resource private RoleService roleService;

    @GetMapping
    @Operation(summary = "List all roles")
    public CommonResult<List<RoleRespVO>> listRoles() {
        return CommonResult.success(roleService.listRoles());
    }

    @SaCheckPermission(CREATE_ROLE)
    @PostMapping
    @Operation(summary = "Create a role")
    public CommonResult<RoleRespVO> createRole(@RequestBody @Valid RoleReqVO roleReqVO) {
        return CommonResult.success(roleService.createRole(roleReqVO));
    }

    @GetMapping("/{roleId}")
    @Operation(summary = "Get a role by id")
    public CommonResult<RoleRespVO> getRole(@PathVariable Long roleId) {
        return CommonResult.success(roleService.getRole(roleId));
    }

    @SaCheckPermission(DELETE_ROLE)
    @DeleteMapping("/{roleId}")
    @Operation(summary = "Delete a role")
    public CommonResult<Boolean> deleteRole(@PathVariable Long roleId) {
        roleService.deleteRole(roleId);
        return CommonResult.success(true);
    }

    @SaCheckPermission(UPDATE_ROLE)
    @PatchMapping("/{roleId}")
    @Operation(summary = "Update a role")
    public CommonResult<RoleRespVO> updateRole(
            @PathVariable Long roleId, @RequestBody @Valid RoleReqVO roleReqVO) {
        return CommonResult.success(roleService.updateRole(roleId, roleReqVO));
    }

    @SaCheckPermission(ASSIGN_ROLE)
    @PostMapping("/assign")
    @Operation(summary = "Assign roles to user")
    public CommonResult<Boolean> assignRole(@RequestBody RoleAssignReqVO reqVO) {
        roleService.assignRoles(reqVO);
        return CommonResult.success(true);
    }
}
