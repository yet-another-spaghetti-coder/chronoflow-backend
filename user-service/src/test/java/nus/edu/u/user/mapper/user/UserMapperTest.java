package nus.edu.u.user.mapper.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import java.util.Set;
import nus.edu.u.user.domain.dataobject.user.UserDO;
import nus.edu.u.user.domain.dto.UserProfileDTO;
import nus.edu.u.user.domain.vo.user.UserProfileRespVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UserMapperTest {

    private UserMapper mapper;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, UserDO.class);
    }

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(UserMapper.class, Mockito.CALLS_REAL_METHODS);
    }

    @Test
    void existsUsername_whenCountPositive_returnsTrue() {
        when(mapper.selectCount(any())).thenReturn(1L);

        boolean exists = mapper.existsUsername("alice", null);

        assertThat(exists).isTrue();
        verify(mapper).selectCount(any());
    }

    @Test
    void existsUsername_whenCountZero_returnsFalse() {
        when(mapper.selectCount(any())).thenReturn(0L);

        boolean exists = mapper.existsUsername("bob", null);

        assertThat(exists).isFalse();
    }

    @Test
    void existsEmail_withExcludeId_returnsResult() {
        when(mapper.selectCount(any())).thenReturn(2L);

        boolean exists = mapper.existsEmail("user@example.com", 10L);

        assertThat(exists).isTrue();
        verify(mapper).selectCount(any());
    }

    @Test
    void selectIdByEmail_whenRecordFound_returnsId() {
        UserDO record = new UserDO();
        record.setId(42L);
        when(mapper.selectOne(any())).thenReturn(record);

        Long id = mapper.selectIdByEmail("user@example.com");

        assertThat(id).isEqualTo(42L);
    }

    @Test
    void selectIdByEmail_whenNoRecord_returnsNull() {
        when(mapper.selectOne(any())).thenReturn(null);

        Long id = mapper.selectIdByEmail("missing@example.com");

        assertThat(id).isNull();
    }

    @Test
    void selectExistingEmails_whenInputNull_returnsEmptySet() {
        Set<String> emails = mapper.selectExistingEmails(null);

        assertThat(emails).isEmpty();
        verify(mapper, never()).selectObjs(any());
    }

    @Test
    void selectExistingEmails_returnsExistingEmails() {
        when(mapper.selectObjs(any())).thenReturn(List.of("a@example.com", "b@example.com"));

        Set<String> result = mapper.selectExistingEmails(List.of("a@example.com", "c@example.com"));

        assertThat(result).containsExactlyInAnyOrder("a@example.com", "b@example.com");
    }

    @Test
    void fromVo_transformsToDto() {
        UserProfileRespVO vo = new UserProfileRespVO();
        vo.setId(1L);
        vo.setName("Alice");
        vo.setEmail("alice@example.com");
        vo.setPhone("12345");
        vo.setRoles(List.of(1L, 2L));
        vo.setRegistered(true);

        UserProfileDTO dto = mapper.fromVo(vo);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("Alice");
        assertThat(dto.getEmail()).isEqualTo("alice@example.com");
        assertThat(dto.getRoles()).containsExactly(1L, 2L);
        assertThat(dto.isRegistered()).isTrue();
    }

    @Test
    void fromVo_whenNull_returnsNull() {
        assertThat(mapper.fromVo(null)).isNull();
    }
}
