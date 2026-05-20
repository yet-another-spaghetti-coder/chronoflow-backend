package nus.edu.u.file.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.web.multipart.MultipartFile;

public class FileValidator implements ConstraintValidator<ValidFile, List<MultipartFile>> {

    private long maxSizeBytes;
    private String[] allowedTypes;
    private Tika tika;

    @Override
    public void initialize(ValidFile annotation) {
        this.maxSizeBytes = annotation.maxSizeMB() * 1024 * 1024;
        this.allowedTypes = annotation.allowedTypes();
        this.tika = new Tika();
    }

    @Override
    public boolean isValid(List<MultipartFile> files, ConstraintValidatorContext ctx) {
        if (files == null || files.isEmpty()) {
            buildViolation(ctx, "At least one file is required");
            return false;
        }

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                buildViolation(ctx, "File must not be empty: " + file.getOriginalFilename());
                return false;
            }

            if (file.getSize() > maxSizeBytes) {
                buildViolation(
                        ctx,
                        String.format(
                                "File '%s' exceeds max size of %dMB",
                                file.getOriginalFilename(), maxSizeBytes / (1024 * 1024)));
                return false;
            }

            String contentType = file.getContentType();
            if (contentType == null || Arrays.stream(allowedTypes).noneMatch(contentType::equals)) {
                buildViolation(
                        ctx,
                        String.format(
                                "File '%s' has disallowed content type: %s",
                                file.getOriginalFilename(), contentType));
                return false;
            }
            String detectedType;
            try {
                Metadata metadata = new Metadata();
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getOriginalFilename());
                detectedType = tika.detect(file.getInputStream(), metadata);
            } catch (Exception e) {
                buildViolation(
                        ctx,
                        "Could not read file: " + Objects.toString(file.getOriginalFilename(), ""));
                return false;
            }

            if (detectedType == null) {
                buildViolation(
                        ctx,
                        String.format(
                                "Unable to detect type for File '%s'", file.getOriginalFilename()));
                return false;
            }
            if (Arrays.stream(allowedTypes).noneMatch(detectedType::equals)) {
                buildViolation(
                        ctx,
                        String.format(
                                "File '%s' has disallowed detected type: %s",
                                file.getOriginalFilename(), detectedType));
                return false;
            }
        }

        return true;
    }

    private void buildViolation(ConstraintValidatorContext ctx, String message) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
