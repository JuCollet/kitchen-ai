package dev.julco.kitchen.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * The instructions sent to every AI client at initialisation (the "brain", docs/PLAN.md §6) are
 * kept in mcp/instructions.md, readable and reviewable as a document, rather than in a YAML
 * string. Spring AI only reads them from its properties, so they are set there before the MCP
 * server is built.
 */
@Configuration(proxyBeanMethods = false)
class McpInstructions {

    static final String LOCATION = "mcp/instructions.md";

    @Bean
    static BeanPostProcessor mcpInstructionsLoader() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof McpServerProperties properties) {
                    properties.setInstructions(read());
                }
                return bean;
            }
        };
    }

    static String read() {
        try (InputStream in = new ClassPathResource(LOCATION).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing MCP instructions: " + LOCATION, e);
        }
    }
}
