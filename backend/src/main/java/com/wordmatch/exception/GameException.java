package com.wordmatch.exception;

/** 携带错误码的业务异常，错误码与前端一一对应 */
public class GameException extends RuntimeException {

    private final String code;

    public GameException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
