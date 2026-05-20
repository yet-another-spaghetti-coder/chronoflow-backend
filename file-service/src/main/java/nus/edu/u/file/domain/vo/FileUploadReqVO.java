package nus.edu.u.file.domain.vo;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import nus.edu.u.common.constant.SecurityConstants;
import nus.edu.u.file.validators.ValidFile;
import org.springframework.web.multipart.MultipartFile;

@Data
public class FileUploadReqVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "taskLogId is required")
    private Long taskLogId;

    @NotNull(message = "eventId is required")
    private Long eventId;

    @ValidFile(
            maxSizeMB = SecurityConstants.MAX_FILE_UPLOAD_SIZE_IN_MB,
            allowedTypes = {
                "image/jpeg",
                "image/png",
                "application/pdf",
                "text/plain", // .txt
                "application/msword", // .doc
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
                "application/vnd.ms-excel", // .xls
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" // .xlsx
            },
            message = "File validation failed")
    private List<MultipartFile> files;
}
