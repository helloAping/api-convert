package cn.ms08.apiconvert.exception;

import org.springframework.http.HttpStatus;

public class GatewayException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;

    public GatewayException(ErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public ErrorCode code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
