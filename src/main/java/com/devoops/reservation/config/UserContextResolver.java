package com.devoops.reservation.config;

import com.devoops.reservation.exception.UnauthorizedException;
import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

public class UserContextResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return UserContext.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            @NonNull MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        String userId = webRequest.getHeader("X-User-Id");
        String role = webRequest.getHeader("X-User-Role");

        if (userId == null || role == null) {
            throw new UnauthorizedException("Missing authentication headers");
        }

        try {
            return new UserContext(UUID.fromString(userId), role);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid user ID format");
        }
    }
}