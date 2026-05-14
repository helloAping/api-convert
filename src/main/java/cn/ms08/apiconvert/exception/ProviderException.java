package cn.ms08.apiconvert.exception;

import org.springframework.http.HttpStatus;

public class ProviderException extends GatewayException {

    public ProviderException(ErrorCode code, HttpStatus status, String message) {
        super(code, status, message);
    }
}
