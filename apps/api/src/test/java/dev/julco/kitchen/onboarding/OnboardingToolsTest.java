package dev.julco.kitchen.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.jayway.jsonpath.DocumentContext;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.McpTestClient;
import dev.julco.kitchen.TestcontainersConfiguration;
import dev.julco.kitchen.profile.ProfileService;
import dev.julco.kitchen.user.AppUser;
import dev.julco.kitchen.user.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** The phase 6 MCP tools as an AI client calls them, with A/B isolation between households. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class OnboardingToolsTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JWKSource<SecurityContext> jwkSource;
    @Autowired ProfileService profiles;

    McpTestClient mcp;
    HouseholdFixture a;
    HouseholdFixture b;

    @BeforeEach
    void createTwoHouseholds() {
        mcp = new McpTestClient(mvc, jwkSource);
        a = HouseholdFixture.create(em, "Alice");
        b = HouseholdFixture.create(em, "Bob");
    }

    @Test
    void the_server_sends_its_instructions_and_lists_the_tools() throws Exception {
        mcp.rpc(a.userId(), "initialize", """
                        {"protocolVersion": "2025-11-25", "capabilities": {}, "clientInfo": {"name": "test", "version": "1"}}""")
                .andExpect(jsonPath("$.result.instructions").value(containsString("Call `get_context` first")));
        mcp.rpc(a.userId(), "tools/list", "{}")
                .andExpect(jsonPath("$.result.tools[*].name").value(hasItems(
                        "get_context", "update_my_profile", "update_household_profile",
                        "list_appliances", "save_appliance", "set_appliance_preference")));
    }

    @Test
    void get_context_only_shows_the_callers_household() throws Exception {
        profiles.giveHealthDataConsent(new AuthenticatedUser(a.userId()));
        mcp.call(a.userId(), "update_my_profile", """
                {"diet": "vegetarian", "allergies": ["peanuts"]}""");

        DocumentContext alice = mcp.call(a.userId(), "get_context", "{}");
        assertThat(alice.read("$.structuredContent.household.name", String.class)).isEqualTo("Household Alice");
        assertThat(alice.read("$.structuredContent.household.members[0].allergies", List.class)).containsExactly("peanuts");
        assertThat(alice.read("$.structuredContent.appliances[0].spec.modes[0].temp_max", Integer.class)).isEqualTo(210);
        assertThat(alice.read("$.structuredContent.onboarding.nextQuestions[0].id", String.class)).isEqualTo("personal.diet");

        DocumentContext bob = mcp.call(b.userId(), "get_context", "{}");
        String bobJson = bob.jsonString();
        assertThat(bobJson).doesNotContain("Alice").doesNotContain("peanuts").doesNotContain(a.applianceId().toString());
        assertThat(bob.read("$.structuredContent.household.members[*].displayName", List.class)).containsExactly("Bob");
    }

    @Test
    void an_appliance_is_saved_from_the_ai_with_its_sheet() throws Exception {
        DocumentContext saved = mcp.call(a.userId(), "save_appliance", """
                {"name": "Cookeo", "brandModel": "Moulinex Cookeo Touch",
                 "spec": {"modes": [{"name": "pressure_cook", "temp_min": 100, "temp_max": 120},
                                    {"name": "brown", "temp_max": 160}],
                          "capacity": "6 L"},
                 "usagePreference": "default", "confirmedByUser": true}""");

        assertThat(saved.read("$.isError", Boolean.class)).isNotEqualTo(Boolean.TRUE);
        assertThat(saved.read("$.structuredContent.usagePreference", String.class)).isEqualTo("default");
        assertThat(mcp.call(a.userId(), "list_appliances", "{}").read("$.structuredContent.appliances[*].name", List.class))
                .containsExactly("Cookeo", "Speedi");
    }

    @Test
    void errors_tell_the_ai_what_to_fix_without_internal_details() throws Exception {
        DocumentContext unconfirmed = mcp.call(a.userId(), "save_appliance", """
                {"name": "Oven", "spec": {"modes": [{"name": "Bake"}]}, "confirmedByUser": false}""");
        assertThat(unconfirmed.read("$.isError", Boolean.class)).isTrue();
        assertThat(unconfirmed.read("$.content[0].text", String.class))
                .contains("confirmedByUser")
                .contains("spec.modes[0].name")
                .doesNotContain("save.input");

        DocumentContext allergies = mcp.call(a.userId(), "update_my_profile", """
                {"allergies": ["peanuts"]}""");
        assertThat(allergies.read("$.isError", Boolean.class)).isTrue();
        assertThat(allergies.read("$.content[0].text", String.class)).contains("health data");

        DocumentContext language = mcp.call(a.userId(), "update_my_profile", """
                {"language": "de"}""");
        assertThat(language.read("$.content[0].text", String.class)).contains("fr, nl or en");
    }

    @Test
    void another_household_appliance_is_reported_as_not_found() throws Exception {
        DocumentContext result = mcp.call(b.userId(), "set_appliance_preference", """
                {"applianceId": "%s", "usagePreference": "on_request"}""".formatted(a.applianceId()));

        assertThat(result.read("$.isError", Boolean.class)).isTrue();
        assertThat(result.read("$.content[0].text", String.class)).contains("use list_appliances");
        assertThat(mcp.call(a.userId(), "list_appliances", "{}").read("$.structuredContent.appliances[0].usagePreference", String.class))
                .isEqualTo("suggest");
    }

    @Test
    void a_person_without_household_is_sent_to_the_website() throws Exception {
        AppUser loner = HouseholdFixture.createUser(em, "Nina");

        DocumentContext result = mcp.call(loner.getId(), "get_context", "{}");

        assertThat(result.read("$.isError", Boolean.class)).isTrue();
        assertThat(result.read("$.content[0].text", String.class)).contains("does not belong to a Kitchen household");
    }
}
