package nus.edu.u.user.service.user;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.shared.rpc.user.RoleBriefDTO;
import nus.edu.u.shared.rpc.user.TenantDTO;
import nus.edu.u.shared.rpc.user.UserInfoDTO;
import nus.edu.u.shared.rpc.user.UserProfileDTO;
import nus.edu.u.shared.rpc.user.UserRpcService;
import nus.edu.u.user.domain.dataobject.role.RoleDO;
import nus.edu.u.user.domain.dataobject.tenant.TenantDO;
import nus.edu.u.user.domain.dataobject.user.UserDO;
import nus.edu.u.user.domain.dataobject.user.UserRoleDO;
import nus.edu.u.user.domain.vo.user.UserProfileRespVO;
import nus.edu.u.user.mapper.role.RoleMapper;
import nus.edu.u.user.mapper.tenant.TenantMapper;
import nus.edu.u.user.mapper.user.UserMapper;
import nus.edu.u.user.mapper.user.UserRoleMapper;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
@Slf4j
@RequiredArgsConstructor
public class UserRpcServiceImpl implements UserRpcService {
    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final UserService userService;

    @Override
    public UserProfileDTO getUserById(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }

        UserDO user = userMapper.selectById(userId);

        if (user == null) {
            return null; // or throw, depending on your contract preference
        }

        return UserProfileDTO.builder()
                .id(user.getId())
                .name(user.getUsername()) // mapping username -> name
                .email(user.getEmail())
                .phone(user.getPhone())
                .roles(Collections.emptyList())
                .isRegistered(true) // or compute properly if you want
                .build();
    }

    @Override
    public boolean exists(Long userId) {
        if (userId == null) {
            log.warn("exists called with null userId");
            return false;
        }

        try {
            return userMapper.selectById(userId) != null;
        } catch (Exception e) {
            log.error("Error checking user existence for userId: {}", userId, e);
            return false;
        }
    }

    @Override
    public Map<Long, UserInfoDTO> getUsers(Collection<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            log.debug("getUsers called with empty userIds");
            return Collections.emptyMap();
        }

        try {
            List<UserDO> users = userMapper.selectBatchIds(userIds);
            if (CollUtil.isEmpty(users)) {
                return Collections.emptyMap();
            }

            Set<Long> ids =
                    users.stream()
                            .map(UserDO::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());

            Map<Long, List<RoleBriefDTO>> rolesByUser = fetchRolesByUserIds(ids);

            return users.stream()
                    .map(
                            user ->
                                    convertToUserInfoDTO(
                                            user,
                                            rolesByUser.getOrDefault(
                                                    user.getId(), Collections.emptyList())))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(UserInfoDTO::getId, user -> user));
        } catch (Exception e) {
            log.error("Error getting users for userIds: {}", userIds, e);
            return Collections.emptyMap();
        }
    }

    @Override
    public List<UserProfileDTO> getEnabledUserProfiles() {
        List<UserProfileRespVO> users = userService.getEnabledUserProfiles();
        return users.stream().map(this::convertToUserProfileDTO).toList();
    }

    @Override
    public TenantDTO getTenantById(Long tenantId) {
        if (tenantId == null) {
            log.warn("getTenantById called with null tenantId");
            return null;
        }

        try {
            TenantDO tenantDO = tenantMapper.selectById(tenantId);
            return convertToTenantDTO(tenantDO);
        } catch (Exception e) {
            log.error("Error getting tenant for tenantId: {}", tenantId, e);
            return null;
        }
    }

    private TenantDTO convertToTenantDTO(TenantDO tenantDO) {
        if (tenantDO == null) {
            return null;
        }

        return TenantDTO.builder()
                .id(tenantDO.getId())
                .name(tenantDO.getName())
                .contactUserId(tenantDO.getContactUserId())
                .contactName(tenantDO.getContactName())
                .contactMobile(tenantDO.getContactMobile())
                .address(tenantDO.getAddress())
                .status(tenantDO.getStatus())
                .tenantCode(tenantDO.getTenantCode())
                .build();
    }

    private Map<Long, List<RoleBriefDTO>> fetchRolesByUserIds(Set<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }

        List<UserRoleDO> relations =
                userRoleMapper.selectList(
                        new LambdaQueryWrapper<UserRoleDO>()
                                .in(UserRoleDO::getUserId, userIds)
                                .eq(UserRoleDO::getDeleted, false));
        if (CollUtil.isEmpty(relations)) {
            return Collections.emptyMap();
        }

        Set<Long> roleIds =
                relations.stream()
                        .map(UserRoleDO::getRoleId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        Map<Long, RoleDO> roleMap = new HashMap<>();
        if (!roleIds.isEmpty()) {
            roleMap =
                    roleMapper.selectBatchIds(roleIds).stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toMap(RoleDO::getId, role -> role));
        }

        Map<Long, List<RoleBriefDTO>> result = new HashMap<>();
        for (UserRoleDO relation : relations) {
            RoleDO role = roleMap.get(relation.getRoleId());
            if (role == null) {
                continue;
            }
            RoleBriefDTO dto =
                    RoleBriefDTO.builder()
                            .id(role.getId())
                            .name(role.getName())
                            .roleKey(role.getRoleKey())
                            .build();
            result.computeIfAbsent(relation.getUserId(), key -> new java.util.ArrayList<>())
                    .add(dto);
        }
        return result;
    }

    private UserProfileDTO convertToUserProfileDTO(UserProfileRespVO vo) {
        if (vo == null) return null;

        return UserProfileDTO.builder()
                .id(vo.getId())
                .name(vo.getName())
                .email(vo.getEmail())
                .phone(vo.getPhone())
                .roles(vo.getRoles())
                .isRegistered(vo.isRegistered())
                .build();
    }

    private UserInfoDTO convertToUserInfoDTO(UserDO userDO, List<RoleBriefDTO> roles) {
        if (userDO == null) {
            return null;
        }

        return UserInfoDTO.builder()
                .id(userDO.getId())
                .username(userDO.getUsername())
                .status(userDO.getStatus())
                .tenantId(userDO.getTenantId())
                .createTime(userDO.getCreateTime())
                .updateTime(userDO.getUpdateTime())
                .email(userDO.getEmail())
                .phone(userDO.getPhone())
                .roles(roles)
                .build();
    }
}
