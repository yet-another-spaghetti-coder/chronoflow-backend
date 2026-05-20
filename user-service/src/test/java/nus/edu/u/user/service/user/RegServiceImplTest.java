package nus.edu.u.user.service.user;

import static nus.edu.u.common.enums.ErrorCodeConstants.ACCOUNT_EXIST;
import static nus.edu.u.common.enums.ErrorCodeConstants.NO_SEARCH_RESULT;
import static nus.edu.u.common.enums.ErrorCodeConstants.REG_FAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import nus.edu.u.common.exception.ServiceException;
import nus.edu.u.user.domain.dataobject.permission.PermissionDO;
import nus.edu.u.user.domain.dataobject.role.RoleDO;
import nus.edu.u.user.domain.dataobject.role.RolePermissionDO;
import nus.edu.u.user.domain.dataobject.tenant.TenantDO;
import nus.edu.u.user.domain.dataobject.user.UserDO;
import nus.edu.u.user.domain.dataobject.user.UserRoleDO;
import nus.edu.u.user.domain.vo.reg.RegMemberReqVO;
import nus.edu.u.user.domain.vo.reg.RegOrganizerReqVO;
import nus.edu.u.user.domain.vo.reg.RegSearchReqVO;
import nus.edu.u.user.domain.vo.reg.RegSearchRespVO;
import nus.edu.u.user.enums.user.UserStatusEnum;
import nus.edu.u.user.mapper.permission.PermissionMapper;
import nus.edu.u.user.mapper.role.RoleMapper;
import nus.edu.u.user.mapper.role.RolePermissionMapper;
import nus.edu.u.user.mapper.tenant.TenantMapper;
import nus.edu.u.user.mapper.user.UserMapper;
import nus.edu.u.user.mapper.user.UserRoleMapper;
import nus.edu.u.user.publisher.organizer.OrganizerNotificationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RegServiceImplTest {

    @Mock private TenantMapper tenantMapper;
    @Mock private UserMapper userMapper;
    @Mock private PermissionMapper permissionMapper;
    @Mock private RolePermissionMapper rolePermissionMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OrganizerNotificationPublisher organizerNotificationPublisher;

    @InjectMocks private RegServiceImpl service;

    private RegSearchReqVO searchReq;
    private final long tenantId = 100L;
    private final long userId = 200L;

    @BeforeEach
    void setUp() {
        searchReq = RegSearchReqVO.builder().organizationId(tenantId).userId(userId).build();
        ReflectionTestUtils.setField(service, "tenantMapper", tenantMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "permissionMapper", permissionMapper);
        ReflectionTestUtils.setField(service, "rolePermissionMapper", rolePermissionMapper);
        ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(service, "userRoleMapper", userRoleMapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
    }

    @Test
    void search_whenTenantAndUserValid_returnsResponse() {
        TenantDO tenant = TenantDO.builder().id(tenantId).name("ChronoFlow").build();
        UserDO user =
                UserDO.builder()
                        .id(userId)
                        .status(UserStatusEnum.PENDING.getCode())
                        .email("member@chrono.sg")
                        .build();
        user.setTenantId(tenantId);

        when(tenantMapper.selectById(tenantId)).thenReturn(tenant);
        when(userMapper.selectByIdWithoutTenant(userId)).thenReturn(user);

        RegSearchRespVO resp = service.search(searchReq);

        assertThat(resp.getOrganizationName()).isEqualTo("ChronoFlow");
        assertThat(resp.getEmail()).isEqualTo("member@chrono.sg");
    }

    @Test
    void search_whenTenantMissing_throwsNoSearchResult() {
        when(tenantMapper.selectById(tenantId)).thenReturn(null);

        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.search(searchReq));
        assertThat(exception.getCode()).isEqualTo(NO_SEARCH_RESULT.getCode());
    }

    @Test
    void search_whenUserTenantMismatch_throwsNoSearchResult() {
        TenantDO tenant = TenantDO.builder().id(tenantId).name("Org").build();
        UserDO user = UserDO.builder().id(userId).status(UserStatusEnum.PENDING.getCode()).build();
        user.setTenantId(999L);

        when(tenantMapper.selectById(tenantId)).thenReturn(tenant);
        when(userMapper.selectByIdWithoutTenant(userId)).thenReturn(user);

        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.search(searchReq));
        assertThat(exception.getCode()).isEqualTo(NO_SEARCH_RESULT.getCode());
    }

    @Test
    void search_whenUserAlreadyActive_throwsAccountExist() {
        TenantDO tenant = TenantDO.builder().id(tenantId).name("Org").build();
        UserDO user = UserDO.builder().id(userId).status(UserStatusEnum.ENABLE.getCode()).build();
        user.setTenantId(tenantId);

        when(tenantMapper.selectById(tenantId)).thenReturn(tenant);
        when(userMapper.selectByIdWithoutTenant(userId)).thenReturn(user);

        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.search(searchReq));
        assertThat(exception.getCode()).isEqualTo(ACCOUNT_EXIST.getCode());
    }

    @Test
    void registerAsMember_updatesPendingUser() {
        RegMemberReqVO request =
                RegMemberReqVO.builder()
                        .userId(userId)
                        .username("new-user")
                        .password("Pass@123")
                        .phone("12345678")
                        .build();
        UserDO pending =
                UserDO.builder().id(userId).status(UserStatusEnum.PENDING.getCode()).build();

        when(userMapper.selectByIdWithoutTenant(userId)).thenReturn(pending);
        when(passwordEncoder.encode(anyString())).thenReturn("ENCODED");
        when(userMapper.updateByIdWithoutTenant(pending)).thenReturn(1);

        boolean updated = service.registerAsMember(request);

        assertThat(updated).isTrue();
        assertThat(pending.getUsername()).isEqualTo("new-user");
        assertThat(pending.getPassword()).isEqualTo("ENCODED");
        assertThat(pending.getStatus()).isEqualTo(UserStatusEnum.ENABLE.getCode());
    }

    @Test
    void registerAsMember_whenUserMissing_throwsRegFail() {
        when(userMapper.selectByIdWithoutTenant(userId)).thenReturn(null);

        RegMemberReqVO request = RegMemberReqVO.builder().userId(userId).build();
        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.registerAsMember(request));
        assertThat(exception.getCode()).isEqualTo(REG_FAIL.getCode());
    }

    @Test
    void registerAsMember_whenAlreadyActivated_throwsAccountExist() {
        UserDO active = UserDO.builder().id(userId).status(UserStatusEnum.ENABLE.getCode()).build();
        when(userMapper.selectByIdWithoutTenant(userId)).thenReturn(active);

        RegMemberReqVO request = RegMemberReqVO.builder().userId(userId).build();
        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.registerAsMember(request));
        assertThat(exception.getCode()).isEqualTo(ACCOUNT_EXIST.getCode());
    }

    @Test
    void registerAsOrganizer_createsTenantUserRolesAndPermissions() {
        RegOrganizerReqVO request =
                RegOrganizerReqVO.builder()
                        .username("organizer")
                        .userPassword("Pwd123!")
                        .userEmail("org@chrono.sg")
                        .mobile("8888")
                        .name("Org Admin")
                        .organizationName("Chrono Org")
                        .organizationAddress("Address")
                        .build();

        when(userMapper.selectByUsername("organizer")).thenReturn(null);
        when(passwordEncoder.encode(anyString())).thenReturn("HASH");
        when(tenantMapper.insert(any()))
                .thenAnswer(
                        invocation -> {
                            TenantDO tenant = invocation.getArgument(0);
                            tenant.setId(tenantId);
                            return 1;
                        });
        when(userMapper.insert(any()))
                .thenAnswer(
                        invocation -> {
                            UserDO user = invocation.getArgument(0);
                            user.setId(userId);
                            return 1;
                        });
        when(tenantMapper.updateById(any(TenantDO.class))).thenReturn(1);

        PermissionDO permission = PermissionDO.builder().id(500L).permissionKey("ALL").build();
        when(permissionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(permission);

        ArgumentCaptor<RoleDO> roleCaptor = ArgumentCaptor.forClass(RoleDO.class);
        when(roleMapper.insert(roleCaptor.capture()))
                .thenAnswer(
                        invocation -> {
                            RoleDO role = invocation.getArgument(0);
                            role.setId(
                                    role.getRoleKey().equals(RegServiceImpl.ORGANIZER_ROLE_KEY)
                                            ? 600L
                                            : 601L);
                            return 1;
                        });
        when(userRoleMapper.insert(any(UserRoleDO.class))).thenReturn(1);
        when(rolePermissionMapper.insert(any(RolePermissionDO.class))).thenReturn(1);

        boolean result = service.registerAsOrganizer(request);

        assertThat(result).isTrue();
        assertThat(roleCaptor.getAllValues())
                .extracting(RoleDO::getRoleKey)
                .containsExactlyInAnyOrder("ORGANIZER", "MEMBER");
    }

    @Test
    void registerAsOrganizer_whenUsernameExists_throwsAccountExist() {
        when(userMapper.selectByUsername("duplicate")).thenReturn(new UserDO());

        RegOrganizerReqVO request = RegOrganizerReqVO.builder().username("duplicate").build();
        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.registerAsOrganizer(request));
        assertThat(exception.getCode()).isEqualTo(ACCOUNT_EXIST.getCode());
    }

    @Test
    void registerAsOrganizer_whenPermissionMissing_throwsRegFail() {
        RegOrganizerReqVO request =
                RegOrganizerReqVO.builder().username("organizer").userPassword("Pwd123!").build();

        when(userMapper.selectByUsername("organizer")).thenReturn(null);
        when(tenantMapper.insert(any()))
                .thenAnswer(
                        invocation -> {
                            TenantDO tenant = invocation.getArgument(0);
                            tenant.setId(tenantId);
                            return 1;
                        });
        when(userMapper.insert(any()))
                .thenAnswer(
                        invocation -> {
                            UserDO user = invocation.getArgument(0);
                            user.setId(userId);
                            return 1;
                        });
        when(tenantMapper.updateById(any(TenantDO.class))).thenReturn(1);
        when(permissionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ServiceException exception =
                assertThrows(ServiceException.class, () -> service.registerAsOrganizer(request));
        assertThat(exception.getCode()).isEqualTo(REG_FAIL.getCode());
    }
}
