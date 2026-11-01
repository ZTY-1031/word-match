package com.wordmatch.exception;

import com.wordmatch.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GameException.class)
    public ApiResponse handleGame(GameException e) {
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse handleOther(Exception e) {
        log.error("接口异常", e);
        return ApiResponse.fail("SERVER_ERROR", "服务端开小差了，稍后再试");
    }
}
