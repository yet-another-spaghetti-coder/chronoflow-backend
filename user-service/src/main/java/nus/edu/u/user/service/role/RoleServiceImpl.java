package nus.edu.u.user.service.role;

import static nus.edu.u.common.enums.ErrorCodeConstants.*;
import static nus.edu.u.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static nus.edu.u.common.utils.exception.ServiceExceptionUtil.exception;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.common.enums.CommonStatusEnum;
import nus.edu.u.framework.security.audit.AuditType;
import nus.edu.u.framework.security.audit.Auditable;
import nus.edu.u.user.domain.dataobject.permission.PermissionDO;
import nus.edu.u.user.domain.dataobject.role.RoleDO;
import nus.edu.u.user.domain.dataobject.role.RolePermissionDO;
import nus.edu.u.user.domain.dataobject.user.UserRoleDO;
import nus.edu.u.user.domain.dto.RoleDTO;
import nus.edu.u.user.domain.dto.UserRoleDTO;
import nus.edu.u.user.domain.vo.permission.PermissionRespVO;
import nus.edu.u.user.domain.vo.role.RoleAssignReqVO;
import nus.edu.u.user.domain.vo.role.RoleReqVO;
import nus.edu.u.user.domain.vo.role.RoleRespVO;
import nus.edu.u.user.mapper.permission.PermissionMapper;
import nus.edu.u.user.mapper.role.RoleMapper;
import nus.edu.u.user.mapper.role.RolePermissionMapper;
import nus.edu.u.user.mapper.user.UserRoleMapper;
import nus.edu.u.user.service.auth.AuthService;
import nus.edu.u.user.service.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class RoleServiceImpl implements RoleService {

    @Resource private RoleMapper roleMapper;

    @Resource private PermissionMapper permissionMapper;

    @Resource private RolePermissionMapper rolePermissionMapper;

    @Resource private UserRoleMapper userRoleMapper;

    @Resource private UserService userService;

    @Resource private AuthService authService;

    public static final String ORGANIZER_ROLE_KEY = "ORGANIZER";

    public static final String MEMBER_ROLE_KEY = "MEMBER";

    public static final String ADMIN_ROLE_KEY = "ADMIN";

    @Override
    public List<RoleRespVO> listRoles() {
        List<RoleDO> roleList = roleMapper.selectList(null);
        roleList =
                roleList.stream()
                        .filter(
                                role ->
                                        !(ORGANIZER_ROLE_KEY.equals(role.getRoleKey())
                                                || ADMIN_ROLE_KEY.equals(role.getRoleKey())))
                        .toList();
        List<RoleRespVO> roleRespVOList = new ArrayList<>();
        if (CollectionUtil.isNotEmpty(roleList)) {
            roleList.forEach(role -> roleRespVOList.add(convert(role)));
        }
        return roleRespVOList;
    }

    @Override
    @Transactional
    @Auditable(operation = "Create Role", type = AuditType.ADMIN_ACTION,
               targetType = "Role", targetId = "#roleReqVO.key")
    public RoleRespVO createRole(RoleReqVO roleReqVO) {
        List<RoleDO> existRole =
                roleMapper.selectList(
                        new LambdaQueryWrapper<RoleDO>()
                                .eq(RoleDO::getRoleKey, roleReqVO.getKey()));
        if (!existRole.isEmpty()) {
            throw exception(EXISTING_ROLE_FAILED);
        }
        RoleDO role =
                RoleDO.builder()
                        .name(roleReqVO.getName())
                        .roleKey(roleReqVO.getKey())
                        .permissionList(roleReqVO.getPermissions())
                        .status(CommonStatusEnum.ENABLE.getStatus())
                        .build();
        boolean isSuccess = roleMapper.insert(role) > 0;
        if (!isSuccess) {
            throw exception(CREATE_ROLE_FAILED);
        }
        RoleRespVO roleRespVO =
                RoleRespVO.builder()
                        .id(role.getId())
                        .name(role.getName())
                        .key(role.getRoleKey())
                        .build();
        if (CollectionUtil.isEmpty(roleReqVO.getPermissions())) {
            return roleRespVO;
        }

        roleReqVO
                .getPermissions()
                .forEach(
                        permission -> {
                            RolePermissionDO rolePermission =
                                    RolePermissionDO.builder()
                                            .roleId(role.getId())
                                            .permissionId(permission)
                                            .build();
                            boolean isSuccessInsert =
                                    rolePermissionMapper.insert(rolePermission) > 0;
                            if (!isSuccessInsert) {
                                throw exception(CREATE_ROLE_FAILED);
                            }
                        });

        return convert(role);
    }

    @Override
    public RoleRespVO getRole(Long roleId) {
        if (ObjUtil.isNull(roleId)) {
            throw exception(BAD_REQUEST);
        }
        RoleDO role = roleMapper.selectById(roleId);
        if (ObjUtil.isNull(role)) {
            throw exception(CANNOT_FIND_ROLE);
        }
        return convert(role);
    }

    @Transactional
    @Auditable(operation = "Delete Role", type = AuditType.ADMIN_ACTION,
               targetType = "Role", targetId = "#roleId")
    public void deleteRole(Long roleId) {
        if (ObjUtil.isNull(roleId)) {
            throw exception(BAD_REQUEST);
        }
        RoleDO role = roleMapper.selectById(roleId);
        if (ObjectUtil.isNull(role)) {
            throw exception(CANNOT_FIND_ROLE);
        }
        if (ORGANIZER_ROLE_KEY.equals(role.getRoleKey())
                || MEMBER_ROLE_KEY.equals(role.getRoleKey())) {
            throw exception(DEFAULT_ROLE);
        }
        List<UserRoleDO> userRoleList =
                userRoleMapper.selectList(
                        new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getRoleId, roleId));
        if (CollectionUtil.isNotEmpty(userRoleList)) {
            throw exception(CANNOT_DELETE_ROLE);
        }
        roleMapper.deleteById(roleId);
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermissionDO>().eq(RolePermissionDO::getRoleId, roleId));
    }

    @Override
    @Transactional
    @Auditable(operation = "Update Role", type = AuditType.ADMIN_ACTION,
               targetType = "Role", targetId = "#roleId")
    public RoleRespVO updateRole(Long roleId, RoleReqVO roleReqVO) {
        if (ObjUtil.isNull(roleId) || ObjUtil.isNull(roleReqVO)) {
            throw exception(BAD_REQUEST);
        }
        RoleDO role = roleMapper.selectById(roleId);
        if (ObjUtil.isNull(role)) {
            throw exception(CANNOT_FIND_ROLE);
        }
        if (ORGANIZER_ROLE_KEY.equals(role.getRoleKey())) {
            throw exception(DEFAULT_ROLE);
        }
        if (MEMBER_ROLE_KEY.equals(role.getRoleKey())
                && (!role.getName().equals(roleReqVO.getName())
                        || !role.getRoleKey().equals(roleReqVO.getKey()))) {
            throw exception(DEFAULT_ROLE);
        }

        Set<Long> existPermissionIds =
                new HashSet<>(
                        CollectionUtil.isEmpty(role.getPermissionList())
                                ? Collections.emptySet()
                                : role.getPermissionList());
        Set<Long> currentPermissionIds =
                new HashSet<>(
                        CollectionUtil.isEmpty(roleReqVO.getPermissions())
                                ? Collections.emptySet()
                                : roleReqVO.getPermissions());

        role.setName(roleReqVO.getName());
        role.setRoleKey(roleReqVO.getKey());
        role.setPermissionList(roleReqVO.getPermissions());
        boolean isSuccess = roleMapper.updateById(role) > 0;
        if (!isSuccess) {
            throw exception(UPDATE_ROLE_FAILED);
        }

        Set<Long> toDelete = new HashSet<>(existPermissionIds);
        toDelete.removeAll(currentPermissionIds);
        if (!CollectionUtil.isEmpty(toDelete)) {
            rolePermissionMapper.delete(
                    new LambdaQueryWrapper<RolePermissionDO>()
                            .in(RolePermissionDO::getPermissionId, toDelete));
        }

        Set<Long> toInsert = new HashSet<>(currentPermissionIds);
        toInsert.removeAll(existPermissionIds);
        if (!CollectionUtil.isEmpty(toInsert)) {
            toInsert.forEach(
                    permissionId -> {
                        RolePermissionDO rolePermissionDO =
                                RolePermissionDO.builder()
                                        .permissionId(permissionId)
                                        .roleId(roleId)
                                        .build();
                        if (rolePermissionMapper.insert(rolePermissionDO) <= 0) {
                            throw exception(UPDATE_ROLE_FAILED);
                        }
                    });
        }

        return convert(role);
    }

    @Override
    @Transactional
    @Auditable(operation = "Assign Roles", type = AuditType.ADMIN_ACTION,
               targetType = "UserRole", targetId = "#reqVO.userId")
    public void assignRoles(RoleAssignReqVO reqVO) {
        if (Objects.isNull(reqVO)) {
            throw exception(BAD_REQUEST);
        }
        UserRoleDTO userRole = userService.selectUserWithRole(reqVO.getUserId());
        if (ObjUtil.isNull(userRole)) {
            throw exception(USER_NOTFOUND);
        }
        Long organizerRoleId =
                userRole.getRoles().stream()
                        .filter(roleDTO -> ORGANIZER_ROLE_KEY.equals(roleDTO.getRoleKey()))
                        .map(RoleDTO::getId)
                        .findFirst()
                        .orElse(null);

        Set<Long> existRoleIds =
                userRole.getRoles().stream().map(RoleDTO::getId).collect(Collectors.toSet());
        Set<Long> currentRoleIds = CollectionUtil.newHashSet(reqVO.getRoles());
        if (ObjectUtil.isNotNull(organizerRoleId)) {
            currentRoleIds.add(organizerRoleId);
        }

        Set<Long> toDelete = new HashSet<>(existRoleIds);
        toDelete.removeAll(currentRoleIds);
        if (CollectionUtil.isNotEmpty(toDelete)) {
            boolean isSuccess =
                    userRoleMapper.delete(
                                    new LambdaQueryWrapper<UserRoleDO>()
                                            .in(UserRoleDO::getRoleId, toDelete))
                            > 0;
            if (!isSuccess) {
                throw exception(ASSIGN_ROLE_FAILED);
            }
        }

        Set<Long> toInsert = new HashSet<>(currentRoleIds);
        toInsert.removeAll(existRoleIds);
        if (CollectionUtil.isNotEmpty(toInsert)) {
            toInsert.forEach(
                    roleId -> {
                        boolean isSuccess =
                                userRoleMapper.insert(
                                                UserRoleDO.builder()
                                                        .roleId(roleId)
                                                        .userId(reqVO.getUserId())
                                                        .build())
                                        > 0;
                        if (!isSuccess) {
                            throw exception(ASSIGN_ROLE_FAILED);
                        }
                    });
        }
    }

    private RoleRespVO convert(RoleDO role) {
        if (ObjUtil.isNull(role)) {
            return null;
        }
        RoleRespVO roleRespVO =
                RoleRespVO.builder()
                        .id(role.getId())
                        .name(role.getName())
                        .isDefault(MEMBER_ROLE_KEY.equals(role.getRoleKey()))
                        .key(role.getRoleKey())
                        .build();
        if (CollectionUtil.isEmpty(role.getPermissionList())) {
            return roleRespVO;
        }
        List<PermissionDO> permissions = permissionMapper.selectBatchIds(role.getPermissionList());
        List<PermissionRespVO> permissionRespVOList =
                permissions.stream()
                        .map(
                                permission ->
                                        PermissionRespVO.builder()
                                                .id(permission.getId())
                                                .name(permission.getName())
                                                .key(permission.getPermissionKey())
                                                .description(permission.getDescription())
                                                .build())
                        .toList();
        roleRespVO.setPermissions(permissionRespVOList);
        return roleRespVO;
    }
}
