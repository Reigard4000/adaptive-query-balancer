package com.mullagaliev.aqpbalancer.exception;

public class BalancerException extends RuntimeException {

    public BalancerException(String message) {
        super(message);
    }

    public BalancerException(String message, Throwable cause) {
        super(message, cause);
    }
}
