package dev.julco.kitchen.mcp;

import io.modelcontextprotocol.spec.McpSchema.Icon;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import java.util.List;
import org.springframework.ai.mcp.customizer.McpStatelessSyncServerCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What Kitchen tells AI clients about itself at initialisation (serverInfo): beyond the name and
 * version of the Spring AI properties, a display title, the website and the Kitchen icon (MCP spec
 * 2025-11-25, Implementation.icons). Clients that support it show the icon next to the connector;
 * the others fall back on the site's favicon, the same image.
 */
@Configuration(proxyBeanMethods = false)
class McpServerInfo {

    @Bean
    McpStatelessSyncServerCustomizer kitchenServerInfo(
            @Value("${spring.ai.mcp.server.name}") String name,
            @Value("${spring.ai.mcp.server.version}") String version,
            @Value("${kitchen.public-url}") String publicUrl) {
        Icon icon = new Icon(publicUrl + "/favicon.svg", "image/svg+xml", List.of("any"), null);
        Implementation info = new Implementation(
                name, "Kitchen", version, "The cooking memory of your household.", List.of(icon), publicUrl);
        return specification -> specification.serverInfo(info);
    }
}
