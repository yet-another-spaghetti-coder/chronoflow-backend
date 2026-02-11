package nus.edu.u.user.service.user;

import static nus.edu.u.common.enums.ErrorCodeConstants.*;
import static nus.edu.u.common.utils.exception.ServiceExceptionUtil.exception;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.common.constant.PermissionConstants;
import nus.edu.u.common.enums.CommonStatusEnum;
import nus.edu.u.shared.rpc.notification.dto.organizer.RegOrganizerReqDTO;
import nus.edu.u.user.domain.dataobject.permission.PermissionDO;
import nus.edu.u.user.domain.dataobject.role.RoleDO;
import nus.edu.u.user.domain.dataobject.role.RolePermissionDO;
import nus.edu.u.user.domain.dataobject.tenant.TenantDO;
import nus.edu.u.user.domain.dataobject.user.UserDO;
import nus.edu.u.user.domain.dataobject.user.UserRoleDO;
import nus.edu.u.user.domain.vo.reg.*;
import nus.edu.u.user.enums.user.UserStatusEnum;
import nus.edu.u.user.mapper.permission.PermissionMapper;
import nus.edu.u.user.mapper.role.RoleMapper;
import nus.edu.u.user.mapper.role.RolePermissionMapper;
import nus.edu.u.user.mapper.tenant.TenantMapper;
import nus.edu.u.user.mapper.user.UserMapper;
import nus.edu.u.user.mapper.user.UserRoleMapper;
import nus.edu.u.user.publisher.organizer.OrganizerNotificationPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Lu Shuwen
 * @date 2025-09-10
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RegServiceImpl implements RegService {

    @Resource
    private TenantMapper tenantMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private PermissionMapper permissionMapper;

    @Resource
    private RolePermissionMapper rolePermissionMapper;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private UserRoleMapper userRoleMapper;

    @Resource
    private PasswordEncoder passwordEncoder;
    // private final OrganizerNotificationPublisher organizerNotificationPublisher;

    public static final String ORGANIZER_REMARK = "Organizer account";

    public static final String ORGANIZER_ROLE_NAME = "Organizer";

    public static final String ORGANIZER_ROLE_KEY = "ORGANIZER";

    public static final String MEMBER_ROLE_NAME = "Member";

    public static final String MEMBER_ROLE_KEY = "MEMBER";

    public static final int ORGANIZATION_CODE_LENGTH = 10;

    public static final int MAX_RETRY_GENERATE_CODE = 5;

    public RegSearchRespVO search(RegSearchReqVO regSearchReqVO) {
        // Select tenant
        TenantDO tenant = tenantMapper.selectById(regSearchReqVO.getOrganizationId());
        if (ObjUtil.isNull(tenant)) {
            throw exception(NO_SEARCH_RESULT);
        }
        // Select user
        UserDO user = userMapper.selectByIdWithoutTenant(regSearchReqVO.getUserId());
        if (ObjUtil.isNull(user)) {
            throw exception(NO_SEARCH_RESULT);
        }
        // Check if user belongs to tenant
        if (!ObjUtil.equals(user.getTenantId(), tenant.getId())) {
            throw exception(NO_SEARCH_RESULT);
        }
        // Check if user has signed up
        if (!ObjUtil.equals(user.getStatus(), UserStatusEnum.PENDING.getCode())) {
            throw exception(ACCOUNT_EXIST);
        }
        // Select position name
        // List<PostDO> postList = postMapper.selectBatchIds(user.getPostList());
        // String post = postList.stream().map(PostDO::getName).collect(Collectors.joining(","));
        // Build return value
        return RegSearchRespVO.builder()
                .organizationName(tenant.getName())
                .email(user.getEmail())
                .build();
    }

    @Override
    public boolean registerAsMember(RegMemberReqVO regMemberReqVO) {
        UserDO user = userMapper.selectByIdWithoutTenant(regMemberReqVO.getUserId());
        if (ObjUtil.isNull(user)) {
            throw exception(REG_FAIL);
        }
        if (!ObjUtil.equals(user.getStatus(), UserStatusEnum.PENDING.getCode())) {
            throw exception(ACCOUNT_EXIST);
        }
        user.setUsername(regMemberReqVO.getUsername());
        user.setPassword(passwordEncoder.encode(regMemberReqVO.getPassword()));
        user.setPhone(regMemberReqVO.getPhone());
        user.setStatus(UserStatusEnum.ENABLE.getCode());

        return userMapper.updateByIdWithoutTenant(user) > 0;
    }

    @Override
    @Transactional
    public boolean registerAsOrganizer(RegOrganizerReqVO regOrganizerReqVO) {
        // check if username exists
        UserDO checkUserDO = userMapper.selectByUsername(regOrganizerReqVO.getUsername());
        if (!ObjUtil.isEmpty(checkUserDO)) {
            throw exception(ACCOUNT_EXIST);
        }
        TenantDO tenant =
                TenantDO.builder()
                        .name(regOrganizerReqVO.getOrganizationName())
                        .contactMobile(regOrganizerReqVO.getMobile())
                        .address(regOrganizerReqVO.getOrganizationAddress())
                        .contactName(regOrganizerReqVO.getName())
                        .build();
        boolean isSuccess = tenantMapper.insert(tenant) > 0;
        if (!isSuccess) {
            throw exception(REG_FAIL);
        }
        // Create and insert user
        UserDO user =
                UserDO.builder()
                        .username(regOrganizerReqVO.getUsername())
                        .email(regOrganizerReqVO.getUserEmail())
                        .phone(regOrganizerReqVO.getMobile())
                        .password(passwordEncoder.encode(regOrganizerReqVO.getUserPassword()))
                        .remark(ORGANIZER_REMARK)
                        .status(UserStatusEnum.ENABLE.getCode())
                        .build();
        user.setTenantId(tenant.getId());
        isSuccess = userMapper.insert(user) > 0;
        if (!isSuccess) {
            throw exception(REG_FAIL);
        }
        // update tenant table with user id
        tenant.setContactUserId(user.getId());
        isSuccess = tenantMapper.updateById(tenant) > 0;
        if (!isSuccess) {
            throw exception(REG_FAIL);
        }
        // Give all organizer permission
        PermissionDO permissionDO =
                permissionMapper.selectOne(
                        new LambdaQueryWrapper<PermissionDO>()
                                .eq(
                                        PermissionDO::getPermissionKey,
                                        PermissionConstants.ALL_SYSTEM_PERMISSION));
        if (ObjUtil.isEmpty(permissionDO)) {
            throw exception(REG_FAIL);
        }
        // Insert organizer role
        RoleDO role =
                RoleDO.builder()
                        .name(ORGANIZER_ROLE_NAME)
                        .roleKey(ORGANIZER_ROLE_KEY)
                        .status(CommonStatusEnum.ENABLE.getStatus())
                        .permissionList(List.of(permissionDO.getId()))
                        .build();
        role.setTenantId(tenant.getId());
        isSuccess = roleMapper.insert(role) > 0;
        if (!isSuccess) {
            throw exception(REG_FAIL);
        }
        // Insert member role
        RoleDO roleMember =
                RoleDO.builder()
                        .name(MEMBER_ROLE_NAME)
                        .roleKey(MEMBER_ROLE_KEY)
                        .status(CommonStatusEnum.ENABLE.getStatus())
                        .build();
        roleMember.setTenantId(tenant.getId());
        isSuccess = roleMapper.insert(roleMember) > 0;
        if (!isSuccess) {
            throw exception(REG_FAIL);
        }
        // Apply organizer role to this user
        UserRoleDO userRole =
                UserRoleDO.builder().userId(user.getId()).roleId(role.getId()).build();
        userRole.setTenantId(tenant.getId());
        isSuccess = userRoleMapper.insert(userRole) > 0;
        if (!isSuccess) {
            throw exception(REG_FAIL);
        }
        RolePermissionDO rolePermissionDO =
                RolePermissionDO.builder()
                        .roleId(role.getId())
                        .permissionId(permissionDO.getId())
                        .build();
        rolePermissionDO.setTenantId(tenant.getId());

        RegOrganizerReqDTO dto =
                RegOrganizerReqDTO.builder()
                        .name(regOrganizerReqVO.getName())
                        .username(user.getUsername())
                        .userEmail(user.getEmail())
                        .mobile(regOrganizerReqVO.getMobile())
                        .organizationName(tenant.getName())
                        .organizationAddress(tenant.getAddress())
                        .organizationCode(regOrganizerReqVO.getOrganizationCode())
                        .build();

        try {
            // organizerNotificationPublisher.sendWelcomeOrganizerEmail(dto);
        } catch (Exception e) {
            log.warn(
                    "Send organizer registration notification failed. email={}",
                    dto.getUserEmail(),
                    e);
        }

        isSuccess = rolePermissionMapper.insert(rolePermissionDO) > 0;
        if (!isSuccess) {
            throw exception(REG_FAIL);
        }
        return isSuccess;
    }

    @Override
    @Transactional
    public boolean registerAsOrganizer(SsoRegOrganizerReqVO ssoRegOrganizerReqVO) {
        //TODO Verify JWT signature
        JWT jwtToken = JWTUtil.parseToken(ssoRegOrganizerReqVO.getJwtToken());
        String email = jwtToken.getPayload("email").toString();
        String name = jwtToken.getPayload("name").toString();
        RegOrganizerReqVO regOrganizerReqVO = RegOrganizerReqVO.builder()
                .name(name)
                .username(email)
                .userEmail(email)
                .mobile(ssoRegOrganizerReqVO.getMobile())
                .organizationName(ssoRegOrganizerReqVO.getOrganizationName())
                .organizationAddress(ssoRegOrganizerReqVO.getOrganizationAddress())
                .organizationCode(ssoRegOrganizerReqVO.getOrganizationCode())
                .userPassword("Pass@123")
                .build();
        return this.registerAsOrganizer(regOrganizerReqVO);
    }
}
