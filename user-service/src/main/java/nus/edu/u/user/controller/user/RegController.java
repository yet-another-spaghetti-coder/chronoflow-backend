package nus.edu.u.user.controller.user;

import static nus.edu.u.common.core.domain.CommonResult.error;
import static nus.edu.u.common.core.domain.CommonResult.success;
import static nus.edu.u.common.enums.ErrorCodeConstants.REG_FAIL;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.common.core.domain.CommonResult;
import nus.edu.u.user.domain.vo.reg.*;
import nus.edu.u.user.service.user.RegService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration controller
 *
 * @author Lu Shuwen
 * @date 2025-09-10
 */
@RestController
@RequestMapping("/users/reg")
@Validated
@Slf4j
public class RegController {

    @Resource private RegService regService;

    @PostMapping("/search")
    public CommonResult<RegSearchRespVO> search(@RequestBody @Valid RegSearchReqVO regSearchReqVO) {
        return success(regService.search(regSearchReqVO));
    }

    @PostMapping("/member")
    public CommonResult<Boolean> registerAsMember(
            @RequestBody @Valid RegMemberReqVO regMemberReqVO) {
        boolean isSuccess = regService.registerAsMember(regMemberReqVO);
        return isSuccess ? success(true) : error(REG_FAIL);
    }

    @PostMapping("/organizer")
    public CommonResult<Boolean> registerAsOrganizer(
            @RequestBody @Valid RegOrganizerReqVO regOrganizerReqVO) {
        boolean isSuccess = regService.registerAsOrganizer(regOrganizerReqVO);
        return isSuccess ? success(true) : error(REG_FAIL);
    }



    @PostMapping("/sso-organizer")
    public CommonResult<Boolean> registerAsOrganizer(
            @RequestBody @Valid SsoRegOrganizerReqVO ssoRegOrganizerReqVO) {
        boolean isSuccess = regService.registerAsOrganizer(ssoRegOrganizerReqVO);
        return isSuccess ? success(true) : error(REG_FAIL);
    }
}
