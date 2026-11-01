package com.wordmatch.dto;

/** 统一响应包：code=0 成功 */
public record ApiResponse(int code, Object data, String message) {

    public static ApiResponse ok(Object data) {
        return new ApiResponse(0, data, "ok");
    }

    public static ApiResponse fail(String code, String message) {
        return new ApiResponse(1, null, message == null ? code : message);
    }

    public static ApiResponse fail(String code, String message, String errorCode) {
        return new ApiResponse(1, java.util.Map.of("errorCode", errorCode), message);
    }
}
