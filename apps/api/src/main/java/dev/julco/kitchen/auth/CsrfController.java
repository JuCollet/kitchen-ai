package dev.julco.kitchen.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring Security only writes the XSRF-TOKEN cookie once the token has been read. The site calls
 * this endpoint at startup, so that the cookie exists before its first POST (passkey sign-in
 * included, which happens before any session exists).
 */
@RestController
class CsrfController {

    @GetMapping("/api/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void csrf(CsrfToken token) {
        token.getToken();
    }
}
