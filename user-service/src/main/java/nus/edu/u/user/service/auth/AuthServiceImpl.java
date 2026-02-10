package nus.edu.u.user.service.auth;

import static nus.edu.u.common.constant.CacheConstants.USER_PERMISSION_KEY;
import static nus.edu.u.common.constant.CacheConstants.USER_ROLE_KEY;
import static nus.edu.u.common.constant.Constants.DEFAULT_DELIMITER;
import static nus.edu.u.common.constant.Constants.SESSION_TENANT_ID;
import static nus.edu.u.common.enums.ErrorCodeConstants.*;
import static nus.edu.u.common.utils.exception.ServiceExceptionUtil.exception;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.common.enums.CommonStatusEnum;
import nus.edu.u.user.domain.dataobject.user.UserDO;
import nus.edu.u.user.domain.dto.RoleDTO;
import nus.edu.u.user.domain.dto.UserPermissionDTO;
import nus.edu.u.user.domain.dto.UserRoleDTO;
import nus.edu.u.user.domain.dto.UserTokenDTO;
import nus.edu.u.user.domain.vo.auth.LoginReqVO;
import nus.edu.u.user.domain.vo.auth.LoginRespVO;
import nus.edu.u.user.domain.vo.auth.UserVO;
import nus.edu.u.user.domain.vo.role.RoleRespVO;
import nus.edu.u.user.service.role.RoleService;
import nus.edu.u.user.service.user.UserService;
import org.springframework.stereotype.Service;

/**
 * Authentication service implementation
 *
 * @author Lu Shuwen
 * @date 2025-08-30
 */
@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    @Resource private UserService userService;

    @Resource private TokenService tokenService;

    @Resource private RoleService roleService;

    @Override
    public UserDO authenticate(String username, String password) {
        // 1.Check username first
        UserDO userDO = userService.getUserByUsername(username);
        if (userDO == null) {
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        // 2.Check password
        if (!userService.isPasswordMatch(password, userDO.getPassword())) {
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        // 3.Check if user is disabled
        if (CommonStatusEnum.isDisable(userDO.getStatus())) {
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
        return userDO;
    }

    public UserDO authenticate(String username) {
        // 1.Check username first
        UserDO userDO = userService.getUserByUsername(username);
        if (userDO == null) {
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        // 3.Check if user is disabled
        if (CommonStatusEnum.isDisable(userDO.getStatus())) {
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
        return userDO;
    }

    @Override
    public LoginRespVO login(LoginReqVO reqVO) {
        // 1.Verify username and password
        UserDO userDO = authenticate(reqVO.getUsername(), reqVO.getPassword());
        // 2.Update user login time
        userDO.setLoginTime(LocalDateTime.now());
        // 3.Create token
        return handleLogin(userDO, reqVO.isRemember(), reqVO.getRefreshToken());
    }


    public LoginRespVO mobileSsoLogin(String token) {
        //TODO: Verify JWT signature
        JWT jwtToken = JWTUtil.parseToken(token);
        String email = jwtToken.getPayload("email").toString();
        UserDO userDO = authenticate(email);
        userDO.setLoginTime(LocalDateTime.now());
        return handleLogin(userDO, false, "");
    }



    private LoginRespVO handleLogin(UserDO userDO, boolean rememberMe, String refreshToken) {
        // 1.Create UserTokenDTO which contains parameters required to create a token
        UserTokenDTO userTokenDTO = new UserTokenDTO();
        BeanUtil.copyProperties(userDO, userTokenDTO);
        userTokenDTO.setRemember(rememberMe);
        // 2.Create two token and set parameters into response object
        StpUtil.login(userDO.getId());
        // 2.1 Set tenant id into context
        StpUtil.getSession().set(SESSION_TENANT_ID, userDO.getTenantId());
        // 3.Check if there already is a refresh token
        if (StrUtil.isEmpty(refreshToken)) {
            refreshToken = tokenService.createRefreshToken(userTokenDTO);
        }
        return getInfo(refreshToken);
    }

    @Override
    public void logout(String token) {
        tokenService.removeToken(token);
        StpUtil.logout();
    }

    @Override
    public LoginRespVO refresh(String refreshToken) {
        // Check if user login or not
        if (StpUtil.isLogin()) {
            return getInfo(refreshToken);
        }
        // Login expired
        // Create access token and expire time
        Long userId = tokenService.getUserIdFromRefreshToken(refreshToken);
        if (ObjUtil.isNull(userId)) {
            throw exception(REFRESH_TOKEN_WRONG);
        }
        // Login user
        StpUtil.login(userId);
        // Build response object
        return getInfo(refreshToken);
    }

    private LoginRespVO getInfo(String refreshToken) {
        UserRoleDTO userRoleDTO =
                userService.selectUserWithRole(Long.parseLong(StpUtil.getLoginId().toString()));
        List<UserPermissionDTO> userPermissionList =
                userService.getUserPermissions(Long.parseLong(StpUtil.getLoginId().toString()));
        if (userRoleDTO == null) {
            throw exception(ACCOUNT_ERROR);
        }
        SaSession session = StpUtil.getSession();
        session.set(
                USER_ROLE_KEY,
                userRoleDTO.getRoles().stream()
                        .map(RoleDTO::getRoleKey)
                        .collect(Collectors.toList()));
        session.set(
                USER_PERMISSION_KEY,
                userPermissionList.stream()
                        .map(UserPermissionDTO::getPermissionKey)
                        .collect(Collectors.toList()));

        UserVO userVO =
                UserVO.builder()
                        .id(userRoleDTO.getUserId())
                        .email(userRoleDTO.getEmail())
                        .name(userRoleDTO.getUsername())
                        .role(
                                userRoleDTO.getRoles().stream()
                                        .map(RoleDTO::getRoleKey)
                                        .collect(Collectors.joining(DEFAULT_DELIMITER)))
                        .build();
        List<RoleRespVO> roleRespVOList =
                userRoleDTO.getRoles().stream()
                        .map(role -> roleService.getRole(role.getId()))
                        .filter(ObjUtil::isNotNull)
                        .toList();
        return LoginRespVO.builder()
                .refreshToken(refreshToken)
                .user(userVO)
                .roles(roleRespVOList)
                .build();
    }
}
