package io.docpilot.common.web.support;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpUtils {

    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (isMissing(ip)) {
            ip = request.getHeader("X-Real-IP");
        }

        if (isMissing(ip)) {
            ip = request.getHeader("Forwarded");
            if (ip != null && ip.contains("for=")) {
                ip = ip.split(";")[0].split("=")[1];
            }
        }

        if (isMissing(ip)) {
            ip = request.getRemoteAddr();
        }

        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    private static boolean isMissing(String ip) {
        return ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip);
    }

}
