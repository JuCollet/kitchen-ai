package dev.julco.kitchen.auth;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param publicUrl the URL people open in their browser (https://kitchen.julco.dev in production,
 *     the Vite dev server locally). Passkeys are bound to its host.
 */
@ConfigurationProperties("kitchen")
public record KitchenProperties(URI publicUrl) {

    /** WebAuthn relying party id: the host, without scheme or port. */
    public String rpId() {
        return publicUrl.getHost();
    }

    /** Origin allowed to use passkeys: scheme, host and port. */
    public String origin() {
        return publicUrl.getPort() == -1
                ? publicUrl.getScheme() + "://" + publicUrl.getHost()
                : publicUrl.getScheme() + "://" + publicUrl.getHost() + ":" + publicUrl.getPort();
    }
}
