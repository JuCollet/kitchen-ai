package dev.julco.kitchen.export;

/** The PDF service did not answer or failed. Carries no detail: the cause is logged by type only. */
public class PdfRenderingException extends RuntimeException {

    public PdfRenderingException() {
        super("PDF rendering failed");
    }
}
