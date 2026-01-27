package nus.edu.u.user.controller.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;
import nus.edu.u.common.core.domain.CommonResult;
import nus.edu.u.user.convert.UserConvert;
import nus.edu.u.user.domain.dataobject.user.UserDO;
import nus.edu.u.user.domain.dto.CreateUserDTO;
import nus.edu.u.user.domain.dto.UpdateUserDTO;
import nus.edu.u.user.domain.vo.user.*;
import nus.edu.u.user.service.excel.ExcelService;
import nus.edu.u.user.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class OrganizerControllerTest {

    @Mock private UserService userService;
    @Mock private UserConvert userConvert;
    @Mock private ExcelService excelService;

    @InjectMocks private OrganizerController controller;

    @Test
    void createUserForOrganizer_convertsAndDelegatesToService() {
        CreateUserReqVO req = new CreateUserReqVO();
        CreateUserDTO dto = CreateUserDTO.builder().email("mail").build();
        when(userConvert.convert(req)).thenReturn(dto);
        when(userService.createUserWithRoleIds(dto)).thenReturn(99L);

        CommonResult<Long> result = controller.createUserForOrganizer(req);

        assertThat(result.getData()).isEqualTo(99L);
        verify(userConvert).convert(req);
        verify(userService).createUserWithRoleIds(dto);
    }

    @Test
    void updateUserForOrganizer_updatesUserAndReturnsRoles() {
        UpdateUserReqVO req = new UpdateUserReqVO();
        req.setRoleIds(List.of(10L));
        UpdateUserDTO dto = new UpdateUserDTO();
        when(userConvert.convert(req)).thenReturn(dto);
        UserDO updated = UserDO.builder().id(15L).email("updated@example.com").build();
        when(userService.updateUserWithRoleIds(any(UpdateUserDTO.class))).thenReturn(updated);
        when(userService.getAliveRoleIdsByUserId(15L)).thenReturn(List.of(1L, 2L));
        UpdateUserRespVO respVO =
                UpdateUserRespVO.builder().id(15L).email("updated@example.com").build();
        when(userConvert.convert(updated)).thenReturn(respVO);

        CommonResult<UpdateUserRespVO> result = controller.updateUserForOrganizer(12L, req);

        ArgumentCaptor<UpdateUserDTO> dtoCaptor = ArgumentCaptor.forClass(UpdateUserDTO.class);
        verify(userService).updateUserWithRoleIds(dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().getId()).isEqualTo(12L);

        assertThat(result.getData()).isSameAs(respVO);
        assertThat(result.getData().getRoleIds()).containsExactly(1L, 2L);
    }

    @Test
    void softDeleteUser_invokesService() {
        CommonResult<Boolean> result = controller.softDeleteUser(33L);

        assertThat(result.getData()).isTrue();
        verify(userService).softDeleteUser(33L);
    }

    @Test
    void restoreUser_invokesService() {
        CommonResult<Boolean> result = controller.restoreUser(34L);

        assertThat(result.getData()).isTrue();
        verify(userService).restoreUser(34L);
    }

    @Test
    void disableUser_invokesService() {
        CommonResult<Boolean> result = controller.disableUser(35L);

        assertThat(result.getData()).isTrue();
        verify(userService).disableUser(35L);
    }

    @Test
    void enableUser_invokesService() {
        CommonResult<Boolean> result = controller.enableUser(36L);

        assertThat(result.getData()).isTrue();
        verify(userService).enableUser(36L);
    }

    @Test
    void getAllUserProfiles_returnsProfiles() {
        List<UserProfileRespVO> profiles = List.of(new UserProfileRespVO());
        when(userService.getAllUserProfiles()).thenReturn(profiles);

        CommonResult<List<UserProfileRespVO>> result = controller.getAllUserProfiles();

        assertThat(result.getData()).isSameAs(profiles);
        verify(userService).getAllUserProfiles();
    }

    @Test
    void bulkUpsertUsers_parsesFileAndDelegates() throws IOException {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "users.xlsx", "application/vnd.ms-excel", new byte[0]);
        List<CreateUserDTO> rows = List.of(CreateUserDTO.builder().email("a").build());
        when(excelService.parseCreateOrUpdateRows(file)).thenReturn(rows);
        BulkUpsertUsersRespVO resp = BulkUpsertUsersRespVO.builder().createdCount(1).build();
        when(userService.bulkUpsertUsers(rows)).thenReturn(resp);

        CommonResult<BulkUpsertUsersRespVO> result = controller.bulkUpsertUsers(file);

        assertThat(result.getData()).isSameAs(resp);
        verify(excelService).parseCreateOrUpdateRows(file);
        verify(userService).bulkUpsertUsers(rows);
    }
}
