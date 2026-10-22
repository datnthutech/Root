package vn.giapha.imports;

import java.io.IOException;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.giapha.auth.AuthUser;
import vn.giapha.common.ApiError;
import vn.giapha.common.ApiException;

@RestController
@RequestMapping("/api/v1/import")
@PreAuthorize("hasRole('ADMIN')")
public class ImportController {
    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final ExcelImportService service;

    public ImportController(ExcelImportService service) { this.service = service; }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau-nhap-gia-pha.xlsx\"")
            .body(TemplateBuilder.build());
    }

    @PostMapping(value = "/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@AuthenticationPrincipal AuthUser me, @RequestParam("file") MultipartFile file,
                                    @RequestParam(defaultValue = "true") boolean dryRun) throws IOException {
        if (file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Chưa chọn tệp");
        ImportReport report = service.run(me, file.getInputStream(), dryRun);
        if (!dryRun && !report.errors().isEmpty()) {
            return ResponseEntity.unprocessableEntity().body(new ApiError("IMPORT_INVALID", "Tệp có lỗi, chưa nhập gì cả", report));
        }
        return ResponseEntity.ok(report);
    }
}
