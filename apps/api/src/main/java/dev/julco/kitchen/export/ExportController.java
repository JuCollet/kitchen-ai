package dev.julco.kitchen.export;

import dev.julco.kitchen.common.NotFoundException;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Download of a menu PDF from the link an AI gave the person. Public route (SecurityConfig): the
 * token in the path is the credential. Unknown, expired or no-longer-allowed token: 404, like any
 * resource that does not exist.
 */
@RestController
class ExportController {

    private final ExportService exports;

    ExportController(ExportService exports) {
        this.exports = exports;
    }

    @GetMapping("/exports/{token}")
    ResponseEntity<byte[]> download(@PathVariable String token) {
        RenderedPdf pdf = exports.render(token).orElseThrow(NotFoundException::new);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                // Shown in the browser (to print it), saved under a clean name.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(pdf.fileName()).build().toString())
                .cacheControl(CacheControl.noStore())
                // The link is a credential: never sent to another site as a Referer.
                .header("Referrer-Policy", "no-referrer")
                .body(pdf.content());
    }

    /** Gotenberg down or failing: the person can try the same link again in a moment. */
    @ExceptionHandler(PdfRenderingException.class)
    ResponseEntity<ProblemDetail> unavailable() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setProperty("code", "pdf_unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }
}
