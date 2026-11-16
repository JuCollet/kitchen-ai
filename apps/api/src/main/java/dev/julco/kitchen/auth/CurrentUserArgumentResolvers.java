package dev.julco.kitchen.auth;

import dev.julco.kitchen.common.NotFoundException;
import dev.julco.kitchen.household.CurrentMember;
import dev.julco.kitchen.household.HouseholdService;
import dev.julco.kitchen.user.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets controllers declare {@link AuthenticatedUser} or {@link CurrentMember} parameters. Both are
 * resolved from the authenticated session, whose name is always the Kitchen user id (see
 * {@link KitchenOidcUser} and {@link PasskeyUserDetailsService}), never from the request content.
 */
@Configuration
class CurrentUserArgumentResolvers implements WebMvcConfigurer {

    private final HouseholdService households;

    CurrentUserArgumentResolvers(HouseholdService households) {
        this.households = households;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticatedUserResolver());
        resolvers.add(new CurrentMemberResolver());
    }

    static AuthenticatedUser authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Not signed in");
        }
        return new AuthenticatedUser(UUID.fromString(authentication.getName()));
    }

    private static final class AuthenticatedUserResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(AuthenticatedUser.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            return authenticatedUser();
        }
    }

    /** A signed-in person without a household gets 404 on household endpoints. */
    private final class CurrentMemberResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(CurrentMember.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            return households.resolveCurrentMember(authenticatedUser().userId())
                    .orElseThrow(NotFoundException::new);
        }
    }
}
