package com.devoops.reservation.config;

import com.devoops.reservation.exception.ForbiddenException;
import com.devoops.reservation.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class RoleAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRole methodAnnotation = handlerMethod.getMethodAnnotation(RequireRole.class);
        RequireRole classAnnotation = handlerMethod.getBeanType().getAnnotation(RequireRole.class);

        RequireRole requireRole = methodAnnotation != null ? methodAnnotation : classAnnotation;
        if (requireRole == null) {
            return true;
        }

        String role = request.getHeader("X-User-Role");
        if (role == null) {
            throw new UnauthorizedException("Missing authentication headers");
        }

        boolean hasRole = Arrays.stream(requireRole.value())
                .anyMatch(r -> r.equalsIgnoreCase(role));

        if (!hasRole) {
            throw new ForbiddenException("Insufficient permissions");
        }

        return true;
    }
}