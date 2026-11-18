package dev.julco.kitchen.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.McpTestClient;
import dev.julco.kitchen.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The menu PDF: its content (through the HTML sent to the PDF service, stubbed here: the real
 * Gotenberg is used locally and in the real test), the download link, and A/B isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MenuPdfTest.StubPdfService.class})
@Transactional
class MenuPdfTest {

    /** Stands in for Gotenberg: remembers the HTML it was given, returns fake PDF bytes. */
    @TestConfiguration
    static class StubPdfService {
        static final AtomicReference<String> lastHtml = new AtomicReference<>();
        static final AtomicBoolean down = new AtomicBoolean();

        @Bean
        @Primary
        PdfRenderer stubRenderer() {
            return html -> {
                if (down.get()) {
                    throw new PdfRenderingException();
                }
                lastHtml.set(html);
                return "%PDF-stub".getBytes(StandardCharsets.US_ASCII);
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired JWKSource<SecurityContext> jwkSource;
    @Autowired ExportService exports;

    McpTestClient mcp;
    HouseholdFixture a;
    HouseholdFixture b;

    @BeforeEach
    void createTwoHouseholds() {
        mcp = new McpTestClient(mvc, jwkSource);
        a = HouseholdFixture.create(em, "Alice"); // French
        b = HouseholdFixture.create(em, "Bob");
        StubPdfService.lastHtml.set(null);
        StubPdfService.down.set(false);
    }

    private String linkFor(HouseholdFixture household, String arguments) throws Exception {
        DocumentContext result = mcp.call(household.userId(), "export_menu_pdf", arguments);
        assertThat(result.read("$.isError", Boolean.class)).isNotEqualTo(Boolean.TRUE);
        String url = result.read("$.structuredContent.url");
        assertThat(url).startsWith(McpTestClient.ISSUER + "/exports/");
        em.flush();
        return url.substring(McpTestClient.ISSUER.length());
    }

    @Test
    void the_pdf_has_the_plan_the_list_and_scaled_recipes_in_the_persons_language() throws Exception {
        // The fixture's chicken recipe is for 4; the menu serves it for 2.
        mcp.call(a.userId(), "create_menu", """
                {"menuId": "%s", "title": "Semaine test", "startsOn": "2026-11-16", "endsOn": "2026-11-22",
                 "items": [{"servedOn": "2026-11-16", "meal": "dinner", "recipeId": "%s", "servings": 2}]}"""
                .formatted(a.menuId(), a.recipeId()));
        String path = linkFor(a, "{}");

        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));

        String html = StubPdfService.lastHtml.get();
        assertThat(html)
                .contains("<html lang=\"fr\">")
                .contains("Semaine test")
                .contains("Lundi 16 novembre")
                .contains("Dîner")
                // Shopping list linked to the menu, with its aisle in French.
                .contains("Liste de courses", "List Alice", "Viande et poisson")
                // 600 g for 4 servings, 300 g for the menu's 2.
                .contains("300 g")
                .contains("air_fry · 200 °C · 20 min");
    }

    @Test
    void household_text_is_escaped_in_the_pdf() throws Exception {
        jdbc.update("update recipe set title = '<script>alert(1)</script>' where id = ?", a.recipeId());
        mvc.perform(get(linkFor(a, "{}"))).andExpect(status().isOk());

        assertThat(StubPdfService.lastHtml.get())
                .doesNotContain("<script>alert(1)</script>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    void a_link_is_reusable_until_it_expires_and_unknown_links_are_404() throws Exception {
        String path = linkFor(a, "{\"menuId\": \"%s\"}".formatted(a.menuId()));
        mvc.perform(get(path)).andExpect(status().isOk());
        mvc.perform(get(path)).andExpect(status().isOk());

        mvc.perform(get("/exports/" + "x".repeat(43)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
        mvc.perform(get("/exports/not-a-token")).andExpect(status().isNotFound());

        jdbc.update("update pdf_export set expires_at = now() - interval '1 minute' where household_id = ?",
                a.householdId());
        mvc.perform(get(path)).andExpect(status().isNotFound());

        exports.deleteExpired();
        assertThat(jdbc.queryForObject("select count(*) from pdf_export where household_id = ?", Integer.class,
                a.householdId())).isZero();
        // Bob's fixture export expires in a week: untouched.
        assertThat(jdbc.queryForObject("select count(*) from pdf_export where household_id = ?", Integer.class,
                b.householdId())).isEqualTo(1);
    }

    @Test
    void a_member_who_left_the_household_no_longer_opens_their_links() throws Exception {
        String path = linkFor(a, "{}");
        jdbc.update("delete from household_member where user_id = ?", a.userId());

        mvc.perform(get(path)).andExpect(status().isNotFound());
    }

    @Test
    void menus_of_another_household_cannot_be_exported() throws Exception {
        DocumentContext result = mcp.call(b.userId(), "export_menu_pdf", "{\"menuId\": \"%s\"}".formatted(a.menuId()));
        assertThat(result.read("$.isError", Boolean.class)).isTrue();
        assertThat(result.read("$.content[0].text", String.class)).contains("Menu not found");

        // Bob's latest menu is his own.
        mvc.perform(get(linkFor(b, "{}"))).andExpect(status().isOk());
        assertThat(StubPdfService.lastHtml.get()).contains("Week Bob").doesNotContain("Alice");
    }

    @Test
    void the_pdf_service_being_down_is_a_503_and_the_link_still_works_afterwards() throws Exception {
        String path = linkFor(a, "{}");
        StubPdfService.down.set(true);
        mvc.perform(get(path))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("pdf_unavailable"));

        StubPdfService.down.set(false);
        mvc.perform(get(path)).andExpect(status().isOk());
    }
}
