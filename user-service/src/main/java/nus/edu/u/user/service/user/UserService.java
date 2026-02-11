package nus.edu.u.user.service.user;

import java.util.List;
import nus.edu.u.user.domain.dataobject.user.UserDO;
import nus.edu.u.user.domain.dto.CreateUserDTO;
import nus.edu.u.user.domain.dto.UpdateUserDTO;
import nus.edu.u.user.domain.dto.UserPermissionDTO;
import nus.edu.u.user.domain.dto.UserRoleDTO;
import nus.edu.u.user.domain.vo.user.BulkUpsertUsersRespVO;
import nus.edu.u.user.domain.vo.user.UserProfileRespVO;

/**
 * User service interface
 *
 * @author Lu Shuwen
 * @date 2025-08-30
 */
public interface UserService {
    /**
     * Select one UserDO object from db
     *
     * @param username name
     * @return UserDO
     */
    UserDO getUserByUsername(String username);

    /**
     * Check if two password are matched
     *
     * @param rawPassword Unencrypted password
     * @param encodedPassword Encrypted password
     * @return Match result
     */
    boolean isPasswordMatch(String rawPassword, String encodedPassword);

    /**
     * Select user and his role by userId
     *
     * @param userId user id
     * @return UserRoleDTO
     */
    UserRoleDTO selectUserWithRole(Long userId);

    UserDO selectUserById(Long userId);

    /**
     * Select user by ID without tenant filtering (for MFA verification)
     *
     * @param userId User ID
     * @return UserDO or null if not found
     */
    UserDO selectUserByIdWithoutTenant(Long userId);

    Long createUserWithRoleIds(CreateUserDTO dto);

    UserDO updateUserWithRoleIds(UpdateUserDTO dto);

    //    UserDO createUser(CreateProfileDTO dto);
    //    UserDO updateUser(UpdateProfileDTO dto);

    void softDeleteUser(Long userId);

    void restoreUser(Long id);

    void disableUser(Long id);

    void enableUser(Long id);

    List<UserProfileRespVO> getAllUserProfiles();

    BulkUpsertUsersRespVO bulkUpsertUsers(List<CreateUserDTO> rawRows);

    boolean tryCreateOrFallbackToUpdate(String email, String remark, List<Long> roleIds);

    List<Long> getAliveRoleIdsByUserId(Long userId);

    List<UserProfileRespVO> getEnabledUserProfiles();

    List<UserPermissionDTO> getUserPermissions(Long userId);

    // Firebase Authentication methods

    /**
     * Get user by Firebase UID
     *
     * @param firebaseUid Firebase UID
     * @return UserDO or null if not found
     */
    UserDO getUserByFirebaseUid(String firebaseUid);

    /**
     * Get user by email
     *
     * @param email User email
     * @return UserDO or null if not found
     */
    UserDO getUserByEmail(String email);

    /**
     * Update user's Firebase UID
     *
     * @param userId User ID
     * @param firebaseUid Firebase UID
     */
    void updateFirebaseUid(Long userId, String firebaseUid);

    /**
     * Create a new user from Firebase authentication
     *
     * @param firebaseUid Firebase UID
     * @param email User email
     * @param name User display name
     * @param organizationName Organization name (optional)
     * @return Created UserDO
     */
    UserDO createUserFromFirebase(String firebaseUid, String email, String name, String organizationName);

    // TOTP methods

    /**
     * Enable TOTP for a user
     *
     * @param userId User ID
     * @param totpSecret TOTP secret key
     */
    void enableTotp(Long userId, String totpSecret);

    /**
     * Disable TOTP for a user
     *
     * @param userId User ID
     */
    void disableTotp(Long userId);
}
