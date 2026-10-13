package ru.itsbelyaev.marketplace.error;

import java.util.Map;
import org.springframework.http.HttpStatus;
import ru.itsbelyaev.marketplace.generated.model.ErrorCode;

public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final ErrorCode code;
    private final Map<String, Object> details;

    public BusinessException(HttpStatus status, ErrorCode code, String message) {
        this(status, code, message, null);
    }

    public BusinessException(HttpStatus status, ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public HttpStatus status() {
        return status;
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
