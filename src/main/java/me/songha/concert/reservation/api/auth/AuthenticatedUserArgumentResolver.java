package me.songha.concert.reservation.api.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class AuthenticatedUserArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String AUTHENTICATED_USER_ID_HEADER = "X-Authenticated-User-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthenticatedUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        String userId = webRequest.getHeader(AUTHENTICATED_USER_ID_HEADER);
        if (userId == null || userId.isBlank()) {
            throw new AuthenticationRequiredException(AUTHENTICATED_USER_ID_HEADER + " header is required.");
        }
        return new AuthenticatedUser(userId);
    }
}
