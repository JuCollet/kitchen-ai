package dev.julco.kitchen.export;

import java.time.Instant;

/** A new download link: the token is shown once, in the URL, and only its hash is stored. */
public record MenuPdfLink(String token, Instant expiresAt, String menuTitle, boolean includesShoppingList) {}
