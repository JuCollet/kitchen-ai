package dev.julco.kitchen.mcp;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.McpTestClient;
import dev.julco.kitchen.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** What an AI client learns about Kitchen at initialisation: its name, title, website and icon. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class McpServerInfoTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JWKSource<SecurityContext> jwkSource;

    @Test
    void the_server_presents_itself_with_the_kitchen_icon() throws Exception {
        HouseholdFixture alice = HouseholdFixture.create(em, "Alice");
        new McpTestClient(mvc, jwkSource).rpc(alice.userId(), "initialize", """
                        {"protocolVersion": "2025-11-25", "capabilities": {},
                         "clientInfo": {"name": "test", "version": "1"}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.serverInfo.name").value("kitchen"))
                .andExpect(jsonPath("$.result.serverInfo.title").value("Kitchen"))
                .andExpect(jsonPath("$.result.serverInfo.websiteUrl").value(McpTestClient.ISSUER))
                .andExpect(jsonPath("$.result.serverInfo.icons[0].src").value(endsWith("/favicon.svg")))
                .andExpect(jsonPath("$.result.serverInfo.icons[0].mimeType").value("image/svg+xml"))
                // The instructions set from mcp/instructions.md are still there.
                .andExpect(jsonPath("$.result.instructions").value(containsString("# Kitchen")));
    }
}
