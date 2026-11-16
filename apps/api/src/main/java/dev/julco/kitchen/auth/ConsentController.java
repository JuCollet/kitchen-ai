package dev.julco.kitchen.auth;

import dev.julco.kitchen.common.NotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the consent page shows about the AI asking for access. The client name is chosen by
 * whoever registered the client, so the page also shows where the tokens will be sent: the hosts
 * of its redirect URIs, which a person can check ("claude.ai", "chatgpt.com").
 */
@RestController
@Validated
class ConsentController {

    private final RegisteredClientRepository clients;

    ConsentController(RegisteredClientRepository clients) {
        this.clients = clients;
    }

    @GetMapping("/api/consent")
    ConsentResponse consent(@RequestParam("client_id") @NotBlank @Size(max = 100) String clientId) {
        RegisteredClient client = clients.findByClientId(clientId);
        if (client == null) {
            throw new NotFoundException();
        }
        List<String> redirectHosts = client.getRedirectUris().stream()
                .map(uri -> URI.create(uri).getHost())
                .distinct()
                .sorted()
                .toList();
        return new ConsentResponse(client.getClientName(), redirectHosts);
    }

    record ConsentResponse(String clientName, List<String> redirectHosts) {}
}
