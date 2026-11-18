package dev.julco.kitchen.export;

/** Turns a self-contained HTML document into a PDF. Gotenberg in practice; a stub in tests. */
public interface PdfRenderer {

    /** @throws PdfRenderingException when the PDF could not be produced */
    byte[] htmlToPdf(String html);
}
