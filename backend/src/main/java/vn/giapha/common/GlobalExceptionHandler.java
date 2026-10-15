package vn.giapha.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(new ApiError(e.code(), e.getMessage(), e.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", "Dữ liệu không hợp lệ", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", "Nội dung gửi lên không đọc được", null));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ApiError("PAYLOAD_TOO_LARGE", "Tệp quá lớn (tối đa 6 MB)", null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", "Không tìm thấy", null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("FORBIDDEN", "Không đủ quyền", null));
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> versionConflict(org.springframework.orm.ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiError("VERSION_CONFLICT", "Dữ liệu đã được người khác sửa, vui lòng tải lại", null));
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    ResponseEntity<ApiError> missingPart(org.springframework.web.multipart.support.MissingServletRequestPartException e) {
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", "Thiếu tệp tải lên: " + e.getRequestPartName(), null));
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> missingParam(org.springframework.web.bind.MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", "Thiếu tham số: " + e.getParameterName(), null));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> typeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", "Tham số không hợp lệ: " + e.getName(), null));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> mediaType(org.springframework.web.HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(new ApiError("UNSUPPORTED_MEDIA_TYPE", "Định dạng nội dung không được hỗ trợ", null));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(org.springframework.dao.DataIntegrityViolationException e) {
        log.warn("Vi phạm ràng buộc dữ liệu", e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiError("CONFLICT", "Dữ liệu xung đột với dữ liệu hiện có, vui lòng tải lại và thử lại", null));
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    ResponseEntity<ApiError> unauthenticated(org.springframework.security.core.AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header("WWW-Authenticate", "Bearer")
            .body(new ApiError("UNAUTHENTICATED", "Cần đăng nhập", null));
    }

    /** Lưới an toàn cuối cùng: lỗi bất ngờ trả 500 JSON thay vì rơi vào ERROR dispatch. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse er) {   // lỗi 4xx/5xx chuẩn của Spring MVC giữ nguyên status
            return ResponseEntity.status(er.getStatusCode())
                .body(new ApiError("HTTP_" + er.getStatusCode().value(), "Yêu cầu không hợp lệ", null));
        }
        log.error("Lỗi không mong đợi", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiError("INTERNAL", "Đã xảy ra lỗi hệ thống, vui lòng thử lại sau", null));
    }
}
