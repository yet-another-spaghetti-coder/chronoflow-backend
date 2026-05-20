package nus.edu.u.file.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.file.domain.vo.FileResultVO;
import nus.edu.u.file.domain.vo.FileUploadReqVO;
import nus.edu.u.file.service.FileStorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Validated
@Slf4j
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<FileResultVO>> uploadToTaskLog(
            @ModelAttribute @Validated FileUploadReqVO req) {
        log.info(
                "Uploading {} file(s) to taskLogId={} for eventId={}",
                req.getFiles() != null ? req.getFiles().size() : 0,
                req.getTaskLogId(),
                req.getEventId());
        List<FileResultVO> results = fileStorageService.uploadToTaskLog(req);
        return ResponseEntity.ok(results);
    }

    /** Download a single file by taskLogId */
    @GetMapping(value = "/{taskLogId}/download", produces = MediaType.APPLICATION_JSON_VALUE)
    public FileResultVO downloadFileByTaskLogId(@PathVariable("taskLogId") Long taskLogId) {
        log.info("Downloading single file for taskLogId={}", taskLogId);
        return fileStorageService.downloadFile(taskLogId);
    }

    /** Download all files attached to a taskLogId */
    @GetMapping(value = "/{taskLogId}/download-all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<FileResultVO> downloadFilesByTaskLogId(@PathVariable("taskLogId") Long taskLogId) {
        log.info("Downloading all files for taskLogId={}", taskLogId);
        return fileStorageService.downloadFilesByTaskLogId(taskLogId);
    }
}
