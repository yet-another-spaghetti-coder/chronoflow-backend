package nus.edu.u.user.mapper.user;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nus.edu.u.user.domain.dataobject.user.UserDO;
import nus.edu.u.user.domain.dto.UserPermissionDTO;
import nus.edu.u.user.domain.dto.UserProfileDTO;
import nus.edu.u.user.domain.dto.UserRoleDTO;
import nus.edu.u.user.domain.vo.user.UserProfileRespVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * User Mapper
 *
 * @author Lu Shuwen
 * @date 2025-08-30
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {

    UserRoleDTO selectUserWithRole(Long userId);

    /**
     * Directly query the original record (including deleted=1), bypassing the MP automatic
     * condition
     */
    UserDO selectRawById(@Param("id") Long id);

    List<UserRoleDTO> selectAllUsersWithRoles();

    @InterceptorIgnore(tenantLine = "true")
    UserDO selectByUsername(String username);

    @InterceptorIgnore(tenantLine = "true")
    UserDO selectUserById(long id);

    // ===== exists series, for Service reuse to avoid duplication of count code =====
    default boolean existsUsername(String username, Long excludeId) {
        return this.selectCount(
                        new LambdaQueryWrapper<UserDO>()
                                .eq(UserDO::getUsername, username)
                                .eq(UserDO::getDeleted, false)
                                .ne(excludeId != null, UserDO::getId, excludeId))
                > 0;
    }

    default boolean existsEmail(String email, Long excludeId) {
        return this.selectCount(
                        new LambdaQueryWrapper<UserDO>()
                                .eq(UserDO::getEmail, email)
                                .eq(UserDO::getDeleted, false)
                                .ne(excludeId != null, UserDO::getId, excludeId))
                > 0;
    }

    default boolean existsPhone(String phone, Long excludeId) {
        return this.selectCount(
                        new LambdaQueryWrapper<UserDO>()
                                .eq(UserDO::getPhone, phone)
                                .eq(UserDO::getDeleted, false)
                                .ne(excludeId != null, UserDO::getId, excludeId))
                > 0;
    }

    /** Find id by email (only find undeleted ones) */
    default Long selectIdByEmail(String email) {
        UserDO one =
                this.selectOne(
                        Wrappers.<UserDO>lambdaQuery()
                                .select(UserDO::getId)
                                .eq(UserDO::getEmail, email)
                                .eq(UserDO::getDeleted, 0)
                                .last("LIMIT 1"));
        return one == null ? null : one.getId();
    }

    /** Batch check existing emails (only check non-deleted emails), return Set<String> */
    default Set<String> selectExistingEmails(Collection<String> emails) {
        if (emails == null || emails.isEmpty()) return Collections.emptySet();
        List<Object> list =
                this.selectObjs(
                        Wrappers.<UserDO>lambdaQuery()
                                .select(UserDO::getEmail)
                                .in(UserDO::getEmail, emails)
                                .eq(UserDO::getDeleted, 0));
        return list.stream().map(o -> (String) o).collect(Collectors.toSet());
    }

    @InterceptorIgnore(tenantLine = "true")
    List<UserPermissionDTO> selectUserWithPermission(Long userId);

    @InterceptorIgnore(tenantLine = "true")
    UserDO selectByIdWithoutTenant(@Param("id") Long id);

    @InterceptorIgnore(tenantLine = "true")
    Integer updateByIdWithoutTenant(UserDO userDO);

    default UserProfileDTO fromVo(UserProfileRespVO vo) {
        if (vo == null) {
            return null;
        }
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(vo.getId());
        dto.setName(vo.getName());
        dto.setEmail(vo.getEmail());
        dto.setPhone(vo.getPhone());
        dto.setRoles(vo.getRoles());
        dto.setRegistered(vo.isRegistered());
        return dto;
    }
}
