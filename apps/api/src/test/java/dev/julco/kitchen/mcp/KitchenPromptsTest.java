package dev.julco.kitchen.mcp;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.McpTestClient;
import dev.julco.kitchen.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** The four MCP prompts: listed, and written in the language of the person who asks. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class KitchenPromptsTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JWKSource<SecurityContext> jwkSource;

    McpTestClient mcp;
    HouseholdFixture alice;

    @BeforeEach
    void createHousehold() {
        mcp = new McpTestClient(mvc, jwkSource);
        alice = HouseholdFixture.create(em, "Alice"); // French
    }

    @Test
    void the_server_declares_prompts_at_initialisation() throws Exception {
        // Clients only ask for prompts/list if the server declared the capability.
        mcp.rpc(alice.userId(), "initialize", """
                        {"protocolVersion": "2025-11-25", "capabilities": {},
                         "clientInfo": {"name": "test", "version": "1"}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.capabilities.prompts").exists());
    }

    @Test
    void the_four_prompts_are_listed() throws Exception {
        mcp.rpc(alice.userId(), "prompts/list", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.prompts[*].name").value(containsInAnyOrder(
                        "weekly_menu", "recipe_of_the_day", "rate_a_recipe", "clear_the_fridge")));
    }

    @Test
    void the_weekly_menu_is_asked_in_the_persons_language_with_their_arguments() throws Exception {
        mcp.rpc(alice.userId(), "prompts/get", """
                        {"name": "weekly_menu", "arguments": {"start": "2026-11-23", "days": "5", "appliances": "que le Cookeo"}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messages[0].role").value("user"))
                .andExpect(jsonPath("$.result.messages[0].content.text").value(allOf(
                        containsString("Prépare le menu de notre foyer pour 5 jours, à partir du 2026-11-23."),
                        containsString("Appareils pour cette période : que le Cookeo."),
                        containsString("préférences d'appareils"),
                        containsString("create_menu"),
                        not(containsString("''")))));
    }

    @Test
    void optional_arguments_can_be_left_out() throws Exception {
        mcp.rpc(alice.userId(), "prompts/get", "{\"name\": \"weekly_menu\", \"arguments\": {}}")
                .andExpect(jsonPath("$.result.messages[0].content.text").value(allOf(
                        containsString("pour 7 jours, à partir du lundi"),
                        not(containsString("Appareils pour")))));
        mcp.rpc(alice.userId(), "prompts/get", "{\"name\": \"rate_a_recipe\", \"arguments\": {}}")
                .andExpect(jsonPath("$.result.messages[0].content.text")
                        .value(containsString("Je veux noter une recette que j'ai cuisinée.")));
        mcp.rpc(alice.userId(), "prompts/get", "{\"name\": \"clear_the_fridge\", \"arguments\": {\"ingredients\": \"poireaux\"}}")
                .andExpect(jsonPath("$.result.messages[0].content.text").value(containsString("Utilise aussi : poireaux.")));
        mcp.rpc(alice.userId(), "prompts/get", "{\"name\": \"recipe_of_the_day\", \"arguments\": {}}")
                .andExpect(jsonPath("$.result.messages[0].content.text")
                        .value(containsString("Qu'est-ce qu'on cuisine aujourd'hui ?")));
    }
}
