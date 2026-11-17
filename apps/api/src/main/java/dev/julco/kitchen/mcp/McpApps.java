package dev.julco.kitchen.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

/**
 * MCP Apps extension (io.modelcontextprotocol/ui, spec 2026-01-26): a tool declares an HTML
 * widget through {@code _meta.ui.resourceUri}; the host reads that {@code ui://} resource and
 * renders it in a sandboxed iframe, which talks to the host with JSON-RPC over postMessage.
 */
public final class McpApps {

    /** MIME type hosts look for to recognise an MCP App. */
    public static final String MIME_TYPE = "text/html;profile=mcp-app";

    /** Where the Maven build copies the widgets built by Vite (packages/shopping-list). */
    private static final String CLASSPATH_DIR = "mcp/ui/";

    private McpApps() {}

    /**
     * Tool metadata pointing to a widget. The flat {@code ui/resourceUri} key is the deprecated
     * form some hosts still read; the official SDK sets both, so do we.
     */
    public static Map<String, Object> toolMeta(String resourceUri) {
        return Map.of("ui", Map.of("resourceUri", resourceUri), "ui/resourceUri", resourceUri);
    }

    /**
     * Resource metadata. The widget is a single self-contained HTML file: it loads nothing and
     * calls nothing outside the host bridge, so the host's default (strictest) CSP is kept.
     */
    public static Map<String, Object> resourceMeta() {
        return Map.of("ui", Map.of("prefersBorder", true));
    }

    /** The built widget, or a placeholder page when the front end was not built. */
    public static String widgetHtml(String fileName) {
        ClassPathResource file = new ClassPathResource(CLASSPATH_DIR + fileName);
        if (!file.exists()) {
            return """
                    <!doctype html><html><body><p>Kitchen widget not built: run pnpm build, \
                    then restart the API.</p></body></html>""";
        }
        try (InputStream in = file.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
