package nus.edu.u.user.service.user;

import nus.edu.u.user.domain.vo.reg.*;

/**
 * Register service
 *
 * @author Lu Shuwen
 * @date 2025-09-10
 */
public interface RegService {

    /**
     * Search user info when sign up
     *
     * @param regSearchReqVO Organization id and user id
     * @return RegSearchRespVO
     */
    RegSearchRespVO search(RegSearchReqVO regSearchReqVO);

    /**
     * User register as member after getting organization info
     *
     * @param regMemberReqVO register property
     */
    boolean registerAsMember(RegMemberReqVO regMemberReqVO);

    boolean registerAsOrganizer(RegOrganizerReqVO regOrganizerReqVO);

    boolean registerAsOrganizer(SsoRegOrganizerReqVO ssoRegOrganizerReqVO);
}
