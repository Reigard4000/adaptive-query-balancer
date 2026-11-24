package com.mullagaliev.aqpbalancer.exception;

public class EstimationException extends RuntimeException {

    public EstimationException(String message) {
        super(message);
    }

    public EstimationException(String message, Throwable cause) {
        super(message, cause);
    }
}
