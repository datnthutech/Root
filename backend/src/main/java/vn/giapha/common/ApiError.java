package vn.giapha.common;

public record ApiError(String code, String message, Object details) {}
