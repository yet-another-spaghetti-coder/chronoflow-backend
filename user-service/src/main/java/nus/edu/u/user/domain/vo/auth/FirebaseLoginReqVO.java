package nus.edu.u.user.domain.vo.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Firebase login request VO.
 * The Firebase ID token is passed via Authorization header.
 */
@Data
@Schema(description = "Firebase login request")
public class FirebaseLoginReqVO {

    @Schema(description = "Whether to remember the login (creates persistent refresh token)", example = "true")
    private boolean remember = true;
}
