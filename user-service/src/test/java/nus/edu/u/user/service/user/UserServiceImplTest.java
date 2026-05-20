package nus.edu.u.user.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import nus.edu.u.common.constant.Constants;
import nus.edu.u.common.enums.ErrorCodeConstants;
import nus.edu.u.common.exception.ServiceException;
import nus.edu.u.common.utils.exception.ServiceExceptionUtil;
import nus.edu.u.user.domain.dataobject.user.UserDO;
import nus.edu.u.user.domain.dataobject.user.UserRoleDO;
import nus.edu.u.user.domain.dto.CreateUserDTO;
import nus.edu.u.user.domain.dto.RoleDTO;
import nus.edu.u.user.domain.dto.UpdateUserDTO;
import nus.edu.u.user.domain.dto.UserRoleDTO;
import nus.edu.u.user.domain.vo.user.BulkUpsertUsersRespVO;
import nus.edu.u.user.enums.user.UserStatusEnum;
import nus.edu.u.user.mapper.role.RoleMapper;
import nus.edu.u.user.mapper.user.UserMapper;
import nus.edu.u.user.mapper.user.UserRoleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    @Spy @InjectMocks private UserServiceImpl service;

    @Mock private UserMapper userMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private nus.edu.u.user.publisher.member.MemberNotificationPublisher memberNotificationPublisher;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, UserDO.class);
        TableInfoHelper.initTableInfo(assistant, UserRoleDO.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "self", service);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "userRoleMapper", userRoleMapper);
        ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        SaTokenContextMockUtil.setMockContext();
        StpUtil.login(999L);
        StpUtil.getSession().set(Constants.SESSION_TENANT_ID, 1L);
    }

    @AfterEach
    void tearDown() {
        try {
            if (StpUtil.isLogin()) {
                StpUtil.logout();
            }
        } catch (Exception ignored) {
        }
        SaTokenContextMockUtil.clearContext();
    }

    @Test
    void getUserByUsername_delegatesToMapper() {
        UserDO user = new UserDO();
        when(userMapper.selectByUsername("alice")).thenReturn(user);

        assertThat(service.getUserByUsername("alice")).isSameAs(user);
    }

    @Test
    void isPasswordMatch_usesPasswordEncoder() {
        when(passwordEncoder.matches("raw" + "salt", "encoded")).thenReturn(true);

        assertThat(service.isPasswordMatch("raw", "encoded", "salt")).isTrue();
    }

    @Test
    void selectUserWithRole_delegatesToMapper() {
        UserRoleDTO dto = new UserRoleDTO();
        when(userMapper.selectUserWithRole(1L)).thenReturn(dto);

        assertThat(service.selectUserWithRole(1L)).isSameAs(dto);
    }

    @Test
    void selectUserById_delegatesToMapper() {
        UserDO user = new UserDO();
        when(userMapper.selectById(2L)).thenReturn(user);

        assertThat(service.selectUserById(2L)).isSameAs(user);
    }

    @Test
    void createUserWithRoleIds_persistsUserAndRoles() {
        CreateUserDTO dto =
                CreateUserDTO.builder()
                        .email("user@example.com")
                        .roleIds(List.of(10L, 11L))
                        .remark("remark")
                        .build();

        when(userMapper.existsEmail("user@example.com", null)).thenReturn(false);
        when(roleMapper.countByIds(dto.getRoleIds())).thenReturn(dto.getRoleIds().size());
        doAnswer(
                        invocation -> {
                            UserDO user = invocation.getArgument(0);
                            user.setId(100L);
                            return 1;
                        })
                .when(userMapper)
                .insert(any(UserDO.class));
        when(userRoleMapper.insert(any(UserRoleDO.class))).thenReturn(1);

        Long id = service.createUserWithRoleIds(dto);

        assertThat(id).isEqualTo(100L);
        verify(userRoleMapper, times(dto.getRoleIds().size())).insert(any(UserRoleDO.class));
    }

    @Test
    void createUserWithRoleIds_forbiddenRoleThrows() {
        CreateUserDTO dto =
                CreateUserDTO.builder().email("user@example.com").roleIds(List.of(1L)).build();

        assertThatThrownBy(() -> service.createUserWithRoleIds(dto))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.ROLE_NOT_FOUND.getCode());
    }

    @Test
    void createUserWithRoleIds_whenEmailExists_throwsEmailExist() {
        CreateUserDTO dto =
                CreateUserDTO.builder().email("dup@example.com").roleIds(List.of(10L)).build();

        when(userMapper.existsEmail("dup@example.com", null)).thenReturn(true);

        assertThatThrownBy(() -> service.createUserWithRoleIds(dto))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.EMAIL_EXIST.getCode());
    }

    @Test
    void createUserWithRoleIds_roleCountMismatch_throwsRoleNotFound() {
        CreateUserDTO dto =
                CreateUserDTO.builder()
                        .email("user@example.com")
                        .roleIds(List.of(10L, 11L))
                        .build();

        when(userMapper.existsEmail("user@example.com", null)).thenReturn(false);
        when(roleMapper.countByIds(dto.getRoleIds())).thenReturn(1);

        assertThatThrownBy(() -> service.createUserWithRoleIds(dto))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.ROLE_NOT_FOUND.getCode());
    }

    @Test
    void createUserWithRoleIds_whenInsertFails_throwsUserInsertFailure() {
        CreateUserDTO dto =
                CreateUserDTO.builder().email("user@example.com").roleIds(List.of(10L)).build();

        when(userMapper.existsEmail("user@example.com", null)).thenReturn(false);
        when(roleMapper.countByIds(dto.getRoleIds())).thenReturn(1);
        when(userMapper.insert(any(UserDO.class))).thenReturn(0);

        assertThatThrownBy(() -> service.createUserWithRoleIds(dto))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.USER_INSERT_FAILURE.getCode());
    }

    @Test
    void createUserWithRoleIds_whenRoleBindingFails_throwsUserRoleBindFailure() {
        CreateUserDTO dto =
                CreateUserDTO.builder().email("user@example.com").roleIds(List.of(10L)).build();

        when(userMapper.existsEmail("user@example.com", null)).thenReturn(false);
        when(roleMapper.countByIds(dto.getRoleIds())).thenReturn(1);
        doAnswer(
                        invocation -> {
                            UserDO user = invocation.getArgument(0);
                            user.setId(100L);
                            return 1;
                        })
                .when(userMapper)
                .insert(any(UserDO.class));
        when(userRoleMapper.insert(any(UserRoleDO.class))).thenReturn(0);

        assertThatThrownBy(() -> service.createUserWithRoleIds(dto))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.USER_ROLE_BIND_FAILURE.getCode());
    }

    @Test
    void createUserWithRoleIds_withoutRoles_skipsBinding() {
        CreateUserDTO dto =
                CreateUserDTO.builder().email("user@example.com").roleIds(List.of()).build();

        when(userMapper.existsEmail("user@example.com", null)).thenReturn(false);
        when(roleMapper.countByIds(dto.getRoleIds())).thenReturn(0);
        doAnswer(
                        invocation -> {
                            UserDO user = invocation.getArgument(0);
                            user.setId(123L);
                            return 1;
                        })
                .when(userMapper)
                .insert(any(UserDO.class));

        Long id = service.createUserWithRoleIds(dto);

        assertThat(id).isEqualTo(123L);
        verify(userRoleMapper, times(0)).insert(any(UserRoleDO.class));
    }

    @Test
    void updateUserWithRoleIds_updatesUserAndSynchronizesRoles() {
        UpdateUserDTO dto =
                UpdateUserDTO.builder()
                        .id(200L)
                        .email("new@example.com")
                        .remark("remark")
                        .roleIds(List.of(10L, 12L))
                        .build();

        UserDO existing = new UserDO();
        existing.setId(200L);
        existing.setDeleted(false);

        UserDO updated = new UserDO();
        updated.setId(200L);
        updated.setEmail("new@example.com");

        when(userMapper.selectById(200L)).thenReturn(existing, updated);
        when(userMapper.existsEmail("new@example.com", 200L)).thenReturn(false);
        when(userMapper.update(any(UserDO.class), any())).thenReturn(1);
        when(roleMapper.countByIds(dto.getRoleIds())).thenReturn(dto.getRoleIds().size());
        when(userRoleMapper.selectAliveRoleIdsByUser(200L)).thenReturn(List.of(10L, 11L));
        when(userRoleMapper.batchLogicalDelete(eq(200L), any())).thenReturn(1);
        when(userRoleMapper.batchRevive(eq(200L), any())).thenReturn(1);
        when(userRoleMapper.insertMissing(eq(200L), any())).thenReturn(1);

        UserDO result = service.updateUserWithRoleIds(dto);

        assertThat(result.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void updateUserWithRoleIds_whenUserMissing_throwsNotFound() {
        UpdateUserDTO dto = UpdateUserDTO.builder().id(1L).build();
        when(userMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.updateUserWithRoleIds(dto))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.USER_NOT_FOUND.getCode());
    }

    @Test
    void updateUserWithRoleIds_whenEmailExists_throwsEmailExist() {
        UpdateUserDTO dto = UpdateUserDTO.builder().id(1L).email("dup@example.com").build();
        UserDO user = new UserDO();
        user.setId(1L);
        user.setDeleted(false);

        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.existsEmail("dup@example.com", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateUserWithRoleIds(dto))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.EMAIL_EXIST.getCode());
    }

    @Test
    void updateUserWithRoleIds_whenUpdateFails_throwsUpdateFailure() {
        UpdateUserDTO dto = UpdateUserDTO.builder().id(1L).email("user@example.com").build();
        UserDO user = new UserDO();
        user.setId(1L);
        user.setDeleted(false);

        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.existsEmail("user@example.com", 1L)).thenReturn(false);
        when(userMapper.update(any(UserDO.class), any())).thenReturn(0);

        assertThatThrownBy(() -> service.updateUserWithRoleIds(dto))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.UPDATE_FAILURE.getCode());
    }

    @Test
    void updateUserWithRoleIds_whenRolesContainForbidden_throwsRoleNotFound() {
        UpdateUserDTO dto = UpdateUserDTO.builder().id(1L).roleIds(List.of(1L)).build();
        UserDO user = new UserDO();
        user.setId(1L);
        user.setDeleted(false);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.update(any(UserDO.class), any())).thenReturn(1);

        assertThatThrownBy(() -> service.updateUserWithRoleIds(dto))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.ROLE_NOT_FOUND.getCode());
    }

    @Test
    void updateUserWithRoleIds_roleCountMismatch_throwsRoleNotFound() {
        UpdateUserDTO dto = UpdateUserDTO.builder().id(1L).roleIds(List.of(10L, 11L)).build();
        UserDO user = new UserDO();
        user.setId(1L);
        user.setDeleted(false);

        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.update(any(UserDO.class), any())).thenReturn(1);
        when(roleMapper.countByIds(dto.getRoleIds())).thenReturn(1);

        assertThatThrownBy(() -> service.updateUserWithRoleIds(dto))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.ROLE_NOT_FOUND.getCode());
    }

    @Test
    void updateUserWithRoleIds_whenUserDeleted_throwsNotFound() {
        UpdateUserDTO dto = UpdateUserDTO.builder().id(1L).build();
        UserDO user = new UserDO();
        user.setDeleted(true);
        when(userMapper.selectById(1L)).thenReturn(user);

        assertThatThrownBy(() -> service.updateUserWithRoleIds(dto))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.USER_NOT_FOUND.getCode());
    }

    @Test
    void updateUserWithRoleIds_whenRoleIdsEmpty_doesNotCheckRoles() {
        UpdateUserDTO dto = UpdateUserDTO.builder().id(1L).roleIds(List.of()).build();
        UserDO user = new UserDO();
        user.setDeleted(false);

        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.update(any(UserDO.class), any())).thenReturn(1);
        when(userMapper.selectById(1L)).thenReturn(user);

        service.updateUserWithRoleIds(dto);

        verify(roleMapper, times(0)).countByIds(any());
    }

    @Test
    void updateUserWithRoleIds_whenRoleIdsNull_skipsRoleSync() {
        UpdateUserDTO dto = UpdateUserDTO.builder().id(1L).roleIds(null).build();
        UserDO user = new UserDO();
        user.setId(1L);
        user.setDeleted(false);

        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.update(any(UserDO.class), any())).thenReturn(1);
        when(userMapper.selectById(1L)).thenReturn(user);

        service.updateUserWithRoleIds(dto);

        verify(roleMapper, times(0)).countByIds(any());
    }

    @Test
    void softDeleteUser_marksDeleted() {
        UserDO existing = new UserDO();
        existing.setId(5L);
        existing.setDeleted(false);

        when(userMapper.selectRawById(5L)).thenReturn(existing);
        when(userMapper.update(any(UserDO.class), any())).thenReturn(1);

        service.softDeleteUser(5L);

        verify(userRoleMapper).delete(any());
    }

    @Test
    void softDeleteUser_whenNotFound_throwsUserNotFound() {
        when(userMapper.selectRawById(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.softDeleteUser(5L))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.USER_NOTFOUND.getCode());
    }

    @Test
    void softDeleteUser_whenAlreadyDeleted_throwsUserAlreadyDeleted() {
        UserDO deleted = new UserDO();
        deleted.setDeleted(true);
        when(userMapper.selectRawById(5L)).thenReturn(deleted);

        assertThatThrownBy(() -> service.softDeleteUser(5L))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.USER_ALREADY_DELETED.getCode());
    }

    @Test
    void softDeleteUser_whenUpdateFails_throwsUpdateFailure() {
        UserDO existing = new UserDO();
        existing.setDeleted(false);
        when(userMapper.selectRawById(5L)).thenReturn(existing);
        when(userMapper.update(any(UserDO.class), any())).thenReturn(0);

        assertThatThrownBy(() -> service.softDeleteUser(5L))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.UPDATE_FAILURE.getCode());
    }

    @Test
    void restoreUser_reactivatesRecord() {
        UserDO deleted = new UserDO();
        deleted.setId(7L);
        deleted.setDeleted(true);

        when(userMapper.selectRawById(7L)).thenReturn(deleted);
        when(userMapper.update(any(UserDO.class), any())).thenReturn(1);

        service.restoreUser(7L);

        verify(userMapper).update(any(UserDO.class), any());
    }

    @Test
    void restoreUser_whenNotFound_throwsUserNotFound() {
        when(userMapper.selectRawById(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.restoreUser(7L))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.USER_NOTFOUND.getCode());
    }

    @Test
    void restoreUser_whenNotDeleted_throwsUserNotDeleted() {
        UserDO user = new UserDO();
        user.setDeleted(false);
        when(userMapper.selectRawById(7L)).thenReturn(user);

        assertThatThrownBy(() -> service.restoreUser(7L))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.USER_NOT_DELETED.getCode());
    }

    @Test
    void restoreUser_whenUpdateFails_throwsUpdateFailure() {
        UserDO user = new UserDO();
        user.setDeleted(true);
        when(userMapper.selectRawById(7L)).thenReturn(user);
        when(userMapper.update(any(UserDO.class), any())).thenReturn(0);

        assertThatThrownBy(() -> service.restoreUser(7L))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.UPDATE_FAILURE.getCode());
    }

    @Test
    void disableUser_whenAlreadyDisabled_throws() {
        UserDO user = new UserDO();
        user.setId(8L);
        user.setStatus(UserStatusEnum.DISABLE.getCode());
        user.setDeleted(false);
        when(userMapper.selectById(8L)).thenReturn(user);

        assertThatThrownBy(() -> service.disableUser(8L))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.USER_ALREADY_DISABLED.getCode());
    }

    @Test
    void enableUser_whenAlreadyEnabled_throws() {
        UserDO user = new UserDO();
        user.setId(9L);
        user.setStatus(UserStatusEnum.ENABLE.getCode());
        user.setDeleted(false);
        when(userMapper.selectById(9L)).thenReturn(user);

        assertThatThrownBy(() -> service.enableUser(9L))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.USER_ALREADY_ENABLED.getCode());
    }

    @Test
    void disableUser_whenUserNotFound_throws() {
        when(userMapper.selectById(8L)).thenReturn(null);

        assertThatThrownBy(() -> service.disableUser(8L))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.USER_NOT_FOUND.getCode());
    }

    @Test
    void disableUser_whenUserDeleted_throws() {
        UserDO user = new UserDO();
        user.setDeleted(true);
        when(userMapper.selectById(8L)).thenReturn(user);

        assertThatThrownBy(() -> service.disableUser(8L))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.USER_NOT_FOUND.getCode());
    }

    @Test
    void disableUser_whenUpdateFails_throwsDisableFailure() {
        UserDO user = new UserDO();
        user.setDeleted(false);
        user.setStatus(UserStatusEnum.ENABLE.getCode());
        when(userMapper.selectById(8L)).thenReturn(user);
        when(userMapper.update(any(UserDO.class), any())).thenReturn(0);

        assertThatThrownBy(() -> service.disableUser(8L))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.USER_DISABLE_FAILURE.getCode());
    }

    @Test
    void disableUser_successfullyUpdatesStatus() {
        UserDO user = new UserDO();
        user.setDeleted(false);
        user.setStatus(UserStatusEnum.ENABLE.getCode());
        when(userMapper.selectById(15L)).thenReturn(user);
        when(userMapper.update(any(UserDO.class), any())).thenReturn(1);

        service.disableUser(15L);

        verify(userMapper).update(any(UserDO.class), any());
    }

    @Test
    void enableUser_whenUserNotFound_throws() {
        when(userMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.enableUser(9L))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.USER_NOT_FOUND.getCode());
    }

    @Test
    void enableUser_whenUserDeleted_throws() {
        UserDO user = new UserDO();
        user.setDeleted(true);
        when(userMapper.selectById(9L)).thenReturn(user);

        assertThatThrownBy(() -> service.enableUser(9L))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.USER_NOT_FOUND.getCode());
    }

    @Test
    void enableUser_whenUpdateFails_throwsEnableFailure() {
        UserDO user = new UserDO();
        user.setDeleted(false);
        user.setStatus(UserStatusEnum.DISABLE.getCode());
        when(userMapper.selectById(9L)).thenReturn(user);
        when(userMapper.update(any(UserDO.class), any())).thenReturn(0);

        assertThatThrownBy(() -> service.enableUser(9L))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.USER_ENABLE_FAILURE.getCode());
    }

    @Test
    void enableUser_successfullyUpdatesStatus() {
        UserDO user = new UserDO();
        user.setDeleted(false);
        user.setStatus(UserStatusEnum.DISABLE.getCode());
        when(userMapper.selectById(16L)).thenReturn(user);
        when(userMapper.update(any(UserDO.class), any())).thenReturn(1);

        service.enableUser(16L);

        verify(userMapper).update(any(UserDO.class), any());
    }

    @Test
    void getAllUserProfiles_excludesCurrentUser() {
        StpUtil.login(500L);
        UserRoleDTO self =
                UserRoleDTO.builder()
                        .userId(500L)
                        .username("self")
                        .email("self@example.com")
                        .status(UserStatusEnum.ENABLE.getCode())
                        .roles(List.of(RoleDTO.builder().id(1L).build()))
                        .build();
        UserRoleDTO other =
                UserRoleDTO.builder()
                        .userId(600L)
                        .username("other")
                        .email("other@example.com")
                        .status(UserStatusEnum.ENABLE.getCode())
                        .roles(List.of(RoleDTO.builder().id(2L).build()))
                        .build();
        when(userMapper.selectAllUsersWithRoles()).thenReturn(List.of(self, other));

        var profiles = service.getAllUserProfiles();

        assertThat(profiles).hasSize(1);
        assertThat(profiles.get(0).getId()).isEqualTo(600L);
    }

    @Test
    void getAllUserProfiles_whenNoUsers_returnsEmptyList() {
        when(userMapper.selectAllUsersWithRoles()).thenReturn(List.of());

        assertThat(service.getAllUserProfiles()).isEmpty();
    }

    @Test
    void getAliveRoleIdsByUserId_delegatesToMapper() {
        when(userRoleMapper.selectRoleIdsByUserId(20L)).thenReturn(List.of(1L, 2L));

        assertThat(service.getAliveRoleIdsByUserId(20L)).containsExactly(1L, 2L);
    }

    @Test
    void getEnabledUserProfiles_filtersDisabledUsersAndSelf() {
        StpUtil.login(100L);
        UserRoleDTO disabled =
                UserRoleDTO.builder()
                        .userId(200L)
                        .username("Disabled")
                        .email("disabled@example.com")
                        .status(UserStatusEnum.DISABLE.getCode())
                        .roles(null)
                        .build();
        UserRoleDTO enabled =
                UserRoleDTO.builder()
                        .userId(201L)
                        .username("Enabled")
                        .email("enabled@example.com")
                        .status(UserStatusEnum.ENABLE.getCode())
                        .roles(null)
                        .build();
        UserRoleDTO self =
                UserRoleDTO.builder()
                        .userId(100L)
                        .username("Self")
                        .email("self@example.com")
                        .status(UserStatusEnum.ENABLE.getCode())
                        .roles(null)
                        .build();
        when(userMapper.selectAllUsersWithRoles()).thenReturn(List.of(disabled, enabled, self));

        var profiles = service.getEnabledUserProfiles();

        assertThat(profiles).hasSize(1);
        assertThat(profiles.get(0).getId()).isEqualTo(201L);
        assertThat(profiles.get(0).isRegistered()).isTrue();
    }

    @Test
    void getAllUserProfiles_handlesNullRolesAndPendingStatus() {
        StpUtil.login(999L);
        UserRoleDTO dto =
                UserRoleDTO.builder()
                        .userId(500L)
                        .username("NoRoles")
                        .email("noroles@example.com")
                        .status(UserStatusEnum.PENDING.getCode())
                        .roles(null)
                        .build();
        when(userMapper.selectAllUsersWithRoles()).thenReturn(List.of(dto));

        var profiles = service.getAllUserProfiles();

        assertThat(profiles.get(0).getRoles()).isEmpty();
        assertThat(profiles.get(0).isRegistered()).isFalse();
    }

    @Test
    void bulkUpsertUsers_countsCreatedAndUpdated() {
        CreateUserDTO row1 =
                CreateUserDTO.builder()
                        .email("new@example.com")
                        .roleIds(List.of(10L))
                        .rowIndex(1)
                        .build();
        CreateUserDTO row2 =
                CreateUserDTO.builder()
                        .email("existing@example.com")
                        .roleIds(List.of(12L))
                        .rowIndex(2)
                        .build();

        doReturn(true, false)
                .when(service)
                .tryCreateOrFallbackToUpdate(anyString(), any(), anyList());

        BulkUpsertUsersRespVO resp = service.bulkUpsertUsers(List.of(row1, row2));

        assertThat(resp.getCreatedCount()).isEqualTo(1);
        assertThat(resp.getUpdatedCount()).isEqualTo(1);
        assertThat(resp.getFailedCount()).isEqualTo(0);
    }

    @Test
    void bulkUpsertUsers_recordsFailureWhenServiceThrowsRuntime() {
        CreateUserDTO row =
                CreateUserDTO.builder()
                        .email("user@example.com")
                        .roleIds(List.of(10L))
                        .rowIndex(4)
                        .build();
        doThrow(new RuntimeException("boom"))
                .when(service)
                .tryCreateOrFallbackToUpdate(anyString(), any(), anyList());

        BulkUpsertUsersRespVO resp = service.bulkUpsertUsers(List.of(row));

        assertThat(resp.getFailedCount()).isEqualTo(1);
        assertThat(resp.getFailures().get(0).getReason()).contains("INTERNAL_ERROR");
    }

    @Test
    void bulkUpsertUsers_recordsFailureForForbiddenRole() {
        CreateUserDTO row =
                CreateUserDTO.builder()
                        .email("user@example.com")
                        .roleIds(List.of(1L))
                        .rowIndex(5)
                        .build();

        BulkUpsertUsersRespVO resp = service.bulkUpsertUsers(List.of(row));

        assertThat(resp.getFailures()).hasSize(1);
        assertThat(resp.getFailures().get(0).getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void bulkUpsertUsers_recordsFailureForBlankEmail() {
        CreateUserDTO row =
                CreateUserDTO.builder().email("   ").roleIds(List.of(10L)).rowIndex(6).build();

        BulkUpsertUsersRespVO resp = service.bulkUpsertUsers(List.of(row));

        assertThat(resp.getFailures()).hasSize(1);
        assertThat(resp.getFailures().get(0).getRowIndex()).isEqualTo(6);
    }

    @Test
    void bulkUpsertUsers_whenInputEmpty_returnsZeroStats() {
        BulkUpsertUsersRespVO resp = service.bulkUpsertUsers(List.of());
        assertThat(resp.getTotalRows()).isZero();
        assertThat(resp.getFailures()).isEmpty();
    }

    @Test
    void bulkUpsertUsers_recordsFailuresForInvalidEmail() {
        CreateUserDTO invalid =
                CreateUserDTO.builder()
                        .email("invalid-email")
                        .roleIds(List.of(10L))
                        .rowIndex(3)
                        .build();

        BulkUpsertUsersRespVO resp = service.bulkUpsertUsers(List.of(invalid));

        assertThat(resp.getFailedCount()).isEqualTo(1);
        assertThat(resp.getFailures().get(0).getRowIndex()).isEqualTo(3);
    }

    @Test
    void tryCreateOrFallbackToUpdate_existingEmailTriggersUpdate() {
        doThrow(ServiceExceptionUtil.exception(ErrorCodeConstants.EMAIL_EXIST))
                .when(service)
                .createUserWithRoleIds(any(CreateUserDTO.class));
        doReturn(new UserDO()).when(service).updateUserWithRoleIds(any(UpdateUserDTO.class));

        when(userMapper.selectIdByEmail("existing@example.com")).thenReturn(3000L);
        when(userMapper.selectById(3000L)).thenReturn(new UserDO());
        when(roleMapper.countByIds(List.of(20L))).thenReturn(1);

        boolean created =
                service.tryCreateOrFallbackToUpdate("existing@example.com", "remark", List.of(20L));

        assertThat(created).isFalse();
    }

    @Test
    void tryCreateOrFallbackToUpdate_missingUserThrows() {
        doThrow(ServiceExceptionUtil.exception(ErrorCodeConstants.EMAIL_EXIST))
                .when(service)
                .createUserWithRoleIds(any(CreateUserDTO.class));
        when(userMapper.selectIdByEmail("missing@example.com")).thenReturn(null);

        assertThatThrownBy(
                        () ->
                                service.tryCreateOrFallbackToUpdate(
                                        "missing@example.com", "remark", List.of(20L)))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.NULL_USERID.getCode());
    }

    @Test
    void tryCreateOrFallbackToUpdate_otherServiceExceptionPropagates() {
        doThrow(ServiceExceptionUtil.exception(ErrorCodeConstants.ROLE_NOT_FOUND))
                .when(service)
                .createUserWithRoleIds(any(CreateUserDTO.class));

        assertThatThrownBy(
                        () ->
                                service.tryCreateOrFallbackToUpdate(
                                        "user@example.com", "remark", List.of(10L)))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.ROLE_NOT_FOUND.getCode());
    }

    @Test
    void tryCreateOrFallbackToUpdate_whenRoleValidationFails_propagatesException() {
        doThrow(ServiceExceptionUtil.exception(ErrorCodeConstants.EMAIL_EXIST))
                .when(service)
                .createUserWithRoleIds(any(CreateUserDTO.class));
        when(userMapper.selectIdByEmail("existing@example.com")).thenReturn(4000L);

        assertThatThrownBy(
                        () ->
                                service.tryCreateOrFallbackToUpdate(
                                        "existing@example.com", "remark", List.of()))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.EMPTY_ROLEIDS.getCode());
    }
}
