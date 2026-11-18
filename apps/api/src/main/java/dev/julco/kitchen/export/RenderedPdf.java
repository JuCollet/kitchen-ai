package dev.julco.kitchen.export;

/** A PDF ready to download, with a file name made of ASCII letters, digits and dashes only. */
public record RenderedPdf(byte[] content, String fileName) {}
