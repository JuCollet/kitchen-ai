package dev.julco.kitchen.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.TestcontainersConfiguration;
import dev.julco.kitchen.common.Language;
import dev.julco.kitchen.user.AppUser;
import dev.julco.kitchen.user.EmailAlreadyUsedException;
import dev.julco.kitchen.user.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SignInTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService users;

    @Test
    void the_first_google_sign_in_creates_the_account_and_the_next_ones_reuse_it() {
        AppUser first = users.signInWithGoogle("google-sub-1", "nina@example.test", "Nina", Language.FR);
        AppUser again = users.signInWithGoogle("google-sub-1", "nina.new@example.test", "Nina N.", Language.EN);

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(again.getLocale()).isEqualTo(Language.FR);
    }

    @Test
    void another_google_account_cannot_take_over_an_existing_email() {
        users.signInWithGoogle("google-sub-1", "nina@example.test", "Nina", Language.FR);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> users.signInWithGoogle("google-sub-2", "NINA@example.test", "Nina", Language.FR))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void google_sign_in_redirects_to_google() throws Exception {
        mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://accounts.google.com/")));
    }

    @Test
    void passkey_sign_in_options_are_public() throws Exception {
        mvc.perform(post("/webauthn/authenticate/options").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge").exists())
                .andExpect(jsonPath("$.rpId").value("localhost"));
    }

    @Test
    void registering_a_passkey_requires_a_session() throws Exception {
        // Spring Security's WebAuthn filter checks authentication itself and answers 400.
        mvc.perform(post("/webauthn/register/options").with(csrf())).andExpect(status().isBadRequest());
    }

    @Test
    void passkeys_are_registered_for_the_kitchen_account() throws Exception {
        AppUser user = HouseholdFixture.createUser(em, "Nina");

        mvc.perform(post("/webauthn/register/options").with(csrf())
                        .with(oidcLogin().idToken(token -> token.subject(user.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.name").value(user.getId().toString()))
                .andExpect(jsonPath("$.rp.id").value("localhost"));
        assertThat(jdbc.queryForObject(
                        "select app_user_id::text from user_entities where name = ?", String.class, user.getId().toString()))
                .isEqualTo(user.getId().toString());
    }

    @Test
    void sign_out_ends_the_session() throws Exception {
        AppUser user = HouseholdFixture.createUser(em, "Nina");

        mvc.perform(post("/logout").with(csrf())
                        .with(oidcLogin().idToken(token -> token.subject(user.getId().toString()))))
                .andExpect(status().isNoContent());
    }
}
