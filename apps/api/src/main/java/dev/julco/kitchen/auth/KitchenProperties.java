package dev.julco.kitchen.auth;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param publicUrl the URL people open in their browser (https://kitchen.julco.dev in production,
 *     the Vite dev server locally). It is also the OAuth issuer, and passkeys are bound to its host.
 * @param oauth OAuth authorization server settings for AI clients
 * @param mcp MCP endpoint settings
 */
@ConfigurationProperties("kitchen")
public record KitchenProperties(URI publicUrl, Oauth oauth, Mcp mcp) {

    public KitchenProperties {
        oauth = oauth == null ? new Oauth(null) : oauth;
        mcp = mcp == null ? new Mcp(List.of()) : mcp;
    }

    /**
     * @param signingKey RSA private key (PKCS#8 PEM) that signs the access tokens of AI clients.
     *     Secret, from an environment variable. When empty, a temporary key is generated at startup
     *     (development only: tokens become invalid at each restart).
     */
    public record Oauth(String signingKey) {}

    /**
     * @param allowedOrigins browser origins allowed to call /mcp besides the public URL itself, for
     *     example the MCP Inspector in development. Requests without an Origin header (AI clients
     *     calling from their servers) are not affected.
     */
    public record Mcp(List<String> allowedOrigins) {

        public Mcp {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }

    /** WebAuthn relying party id: the host, without scheme or port. */
    public String rpId() {
        return publicUrl.getHost();
    }

    /** Origin of the site: scheme, host and port. */
    public String origin() {
        return publicUrl.getPort() == -1
                ? publicUrl.getScheme() + "://" + publicUrl.getHost()
                : publicUrl.getScheme() + "://" + publicUrl.getHost() + ":" + publicUrl.getPort();
    }

    /** OAuth issuer of the tokens given to AI clients. */
    public String issuer() {
        return origin();
    }

    /** Resource identifier of the MCP server (RFC 8707, RFC 9728): the audience of its tokens. */
    public String mcpResource() {
        return origin() + "/mcp";
    }
}
