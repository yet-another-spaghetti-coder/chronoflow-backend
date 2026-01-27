package nus.edu.u.user.convert;

import nus.edu.u.user.domain.dataobject.user.UserDO;
import nus.edu.u.user.domain.dto.CreateUserDTO;
import nus.edu.u.user.domain.dto.UpdateUserDTO;
import nus.edu.u.user.domain.vo.user.CreateUserReqVO;
import nus.edu.u.user.domain.vo.user.UpdateUserReqVO;
import nus.edu.u.user.domain.vo.user.UpdateUserRespVO;
import org.mapstruct.Mapper;

/** VO ↔ DTO ↔ DO 转换器 */
@Mapper(componentModel = "spring")
public interface UserConvert {

    CreateUserDTO convert(CreateUserReqVO vo);

    UpdateUserDTO convert(UpdateUserReqVO vo);

    UpdateUserRespVO convert(UserDO userDO);
}
