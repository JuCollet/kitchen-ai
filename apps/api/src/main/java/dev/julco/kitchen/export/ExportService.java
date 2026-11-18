package dev.julco.kitchen.export;

import dev.julco.kitchen.common.NotFoundException;
import dev.julco.kitchen.household.CurrentMember;
import dev.julco.kitchen.household.HouseholdService;
import dev.julco.kitchen.menu.MenuService;
import dev.julco.kitchen.menu.MenuView;
import dev.julco.kitchen.shopping.ShoppingListService;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * Menu PDF download links. Creating one needs the current member; opening one needs only the
 * link: the random token (256 bits) is the credential, valid for {@link #VALIDITY}, usable several
 * times in that window (chat apps often open a link once to preview it). Only its SHA-256 hash is
 * stored. The PDF is not stored either: it is rendered again at each download, from the menu as it
 * is then, for the member who asked, who must still belong to the household.
 */
@Service
@Validated
public class ExportService {

    static final Duration VALIDITY = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final PdfExportRepository exports;
    private final MenuService menus;
    private final ShoppingListService shoppingLists;
    private final HouseholdService households;
    private final MenuPdfService menuPdf;
    private final PdfRenderer renderer;

    public ExportService(
            PdfExportRepository exports,
            MenuService menus,
            ShoppingListService shoppingLists,
            HouseholdService households,
            MenuPdfService menuPdf,
            PdfRenderer renderer) {
        this.exports = exports;
        this.menus = menus;
        this.shoppingLists = shoppingLists;
        this.households = households;
        this.menuPdf = menuPdf;
        this.renderer = renderer;
    }

    /** A link to the PDF of a menu of the household, or of its latest menu ({@code menuId} null). */
    @Transactional
    public MenuPdfLink createMenuLink(@NotNull CurrentMember currentMember, UUID menuId) {
        MenuView menu = menuId == null
                ? menus.latest(currentMember).orElseThrow(NotFoundException::new)
                : menus.get(currentMember, menuId);
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        Instant expiresAt = Instant.now().plus(VALIDITY);
        exports.save(new PdfExport(currentMember.householdId(), menu.id(), currentMember.userId(), hash(token),
                currentMember.language(), expiresAt));
        boolean withList = shoppingLists.latestForMenu(currentMember, menu.id()).isPresent();
        return new MenuPdfLink(token, expiresAt, menu.title(), withList);
    }

    /**
     * The PDF behind a link, or empty for an unknown or expired token, or when the member who asked
     * for it has left the household (same answer for all: CLAUDE.md rule 2).
     *
     * @throws PdfRenderingException when the PDF service failed
     */
    public Optional<RenderedPdf> render(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            return Optional.empty();
        }
        Optional<PdfExport> found = exports.findByTokenHashAndExpiresAtAfter(hash(token), Instant.now());
        if (found.isEmpty() || found.get().getRequestedBy() == null) {
            return Optional.empty();
        }
        PdfExport export = found.get();
        Optional<CurrentMember> member = households.resolveCurrentMember(export.getRequestedBy())
                .filter(candidate -> candidate.householdId().equals(export.getHouseholdId()));
        if (member.isEmpty()) {
            return Optional.empty();
        }
        String html;
        try {
            html = menuPdf.html(member.get(), export.getMenuId(), export.getLanguage());
        } catch (NotFoundException e) {
            return Optional.empty();
        }
        // Outside any transaction: the database connection is not held while Chromium works.
        return Optional.of(new RenderedPdf(renderer.htmlToPdf(html), "kitchen-menu.pdf"));
    }

    /** Expired links are useless; their rows go away every hour. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public void deleteExpired() {
        int deleted = exports.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Deleted {} expired PDF links", deleted);
        }
    }

    private static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
