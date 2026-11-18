package dev.julco.kitchen.export;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Gotenberg (headless Chromium in its own container) renders the HTML to PDF: route
 * /forms/chromium/convert/html, the document sent as a file named index.html. Gotenberg runs
 * without internet access and without JavaScript (compose.yaml, NetworkPolicy in the cluster): the
 * HTML must be self-contained, which the template is (no external font, image or script).
 */
@Component
class GotenbergPdfRenderer implements PdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(GotenbergPdfRenderer.class);

    private final RestClient client;

    GotenbergPdfRenderer(@Value("${kitchen.gotenberg.url}") String gotenbergUrl) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        // A plain builder: Spring Boot 4 only provides a RestClient.Builder bean with its restclient
        // module, not worth a dependency for one internal call.
        this.client = RestClient.builder().baseUrl(gotenbergUrl).requestFactory(requestFactory).build();
    }

    @Override
    public byte[] htmlToPdf(String html) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("files", new ByteArrayResource(html.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "index.html";
            }
        });
        // Page size and margins come from the template's @page rule.
        form.add("preferCssPageSize", "true");
        form.add("printBackground", "true");
        try {
            byte[] pdf = client.post()
                    .uri("/forms/chromium/convert/html")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(byte[].class);
            if (pdf == null || pdf.length == 0) {
                throw new PdfRenderingException();
            }
            return pdf;
        } catch (RestClientException e) {
            log.error("Gotenberg failed with {}", e.getClass().getName());
            throw new PdfRenderingException();
        }
    }
}
