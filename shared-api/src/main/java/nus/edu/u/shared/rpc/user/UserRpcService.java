package nus.edu.u.shared.rpc.user;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface UserRpcService {

    boolean exists(Long userId);

    Map<Long, UserInfoDTO> getUsers(Collection<Long> userIds);

    TenantDTO getTenantById(Long tenantId);

    List<UserProfileDTO> getEnabledUserProfiles();

    UserProfileDTO getUserById(Long userId);
}
