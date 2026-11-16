package dev.julco.kitchen.auth;

import dev.julco.kitchen.common.NotFoundException;
import dev.julco.kitchen.household.CurrentMember;
import dev.julco.kitchen.user.AuthenticatedUser;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets controllers declare {@link AuthenticatedUser} or {@link CurrentMember} parameters. Both are
 * resolved from the authentication (see {@link CurrentUsers}), never from the request content.
 */
@Configuration
class CurrentUserArgumentResolvers implements WebMvcConfigurer {

    private final CurrentUsers currentUsers;

    CurrentUserArgumentResolvers(CurrentUsers currentUsers) {
        this.currentUsers = currentUsers;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new Resolver(AuthenticatedUser.class, () -> currentUsers.authenticatedUser()));
        // A signed-in person without a household gets 404 on household endpoints.
        resolvers.add(new Resolver(
                CurrentMember.class, () -> currentUsers.currentMember().orElseThrow(NotFoundException::new)));
    }

    private record Resolver(Class<?> type, Supplier<Object> value) implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(type);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            return value.get();
        }
    }
}
