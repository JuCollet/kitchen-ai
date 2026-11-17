package dev.julco.kitchen.pantry;

import dev.julco.kitchen.common.Aisle;
import dev.julco.kitchen.common.InvalidInputException;
import dev.julco.kitchen.household.CurrentMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * The household's pantry: what is at home, with approximate quantities. One item per name and
 * household, case-insensitive (unique index of V7). Every read and write is filtered by the
 * current member's household (CLAUDE.md rule 1).
 */
@Service
@Validated
@Transactional
public class PantryService {

    /** Upper bound of a pantry, so that one household cannot grow it without limit (rule 4). */
    static final int MAX_ITEMS = 300;

    static final int SUMMARY_SIZE = 100;

    private final PantryItemRepository pantry;

    public PantryService(PantryItemRepository pantry) {
        this.pantry = pantry;
    }

    @Transactional(readOnly = true)
    public List<PantryItemView> list(@NotNull CurrentMember currentMember) {
        return pantry.findAllByHouseholdIdOrderByName(currentMember.householdId()).stream()
                .map(PantryItemView::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public PantrySummary summary(@NotNull CurrentMember currentMember) {
        List<PantryItem> items = pantry.findAllByHouseholdIdOrderByName(currentMember.householdId());
        List<PantrySummary.Line> lines = items.stream()
                .sorted(Comparator.comparing(PantryItem::getUpdatedAt).reversed())
                .limit(SUMMARY_SIZE)
                .sorted(Comparator.comparing(item -> item.getName().toLowerCase(Locale.ROOT)))
                .map(item -> new PantrySummary.Line(item.getName(), item.getQuantity()))
                .toList();
        return new PantrySummary(items.size(), lines);
    }

    /**
     * Adds or updates items by name, then removes the named ones. Removing a name the pantry does
     * not contain is not an error (the pantry is approximate): it is reported in {@code notFound}.
     */
    public PantryUpdateResult update(@NotNull CurrentMember currentMember, @NotNull @Valid PantryUpdate update) {
        checkNoDuplicates(update);
        for (PantryUpsert item : update.upsert()) {
            String name = item.name().strip();
            String quantity = blankToNull(item.quantity());
            Aisle aisle = item.aisle() == null ? null : Aisle.fromCode(item.aisle());
            Optional<PantryItem> existing = pantry.findByHouseholdIdAndNameIgnoringCase(currentMember.householdId(), name);
            if (existing.isPresent()) {
                existing.get().update(quantity, aisle, PantrySource.MANUAL);
            } else {
                pantry.save(new PantryItem(currentMember.householdId(), name, quantity,
                        aisle == null ? Aisle.OTHER : aisle, PantrySource.MANUAL));
            }
        }
        List<String> notFound = new ArrayList<>();
        for (String name : update.remove()) {
            pantry.findByHouseholdIdAndNameIgnoringCase(currentMember.householdId(), name.strip())
                    .ifPresentOrElse(
                            item -> pantry.deleteByHouseholdIdAndId(currentMember.householdId(), item.getId()),
                            () -> notFound.add(name.strip()));
        }
        if (pantry.countByHouseholdId(currentMember.householdId()) > MAX_ITEMS) {
            throw new InvalidInputException("The pantry is limited to " + MAX_ITEMS
                    + " items. Remove items that are used up before adding new ones.");
        }
        return new PantryUpdateResult(list(currentMember), notFound);
    }

    /**
     * A shopping list item was checked: it is now at home. Adds it to the pantry, or updates the
     * quantity of the item with the same name. Returns what was done, or empty when the pantry is
     * full: checking an item in the store must never fail because of the pantry.
     */
    public Optional<PantryLink> addFromShopping(
            @NotNull CurrentMember currentMember, @NotNull String name, String quantity, @NotNull Aisle aisle) {
        String cleanName = name.strip();
        Optional<PantryItem> existing = pantry.findByHouseholdIdAndNameIgnoringCase(currentMember.householdId(), cleanName);
        if (existing.isPresent()) {
            PantryItem item = existing.get();
            String previous = item.getQuantity();
            item.update(quantity, null, PantrySource.SHOPPING);
            return Optional.of(new PantryLink(item.getId(), false, previous));
        }
        if (pantry.countByHouseholdId(currentMember.householdId()) >= MAX_ITEMS) {
            return Optional.empty();
        }
        PantryItem created = pantry.save(new PantryItem(currentMember.householdId(), cleanName, quantity, aisle,
                PantrySource.SHOPPING));
        return Optional.of(new PantryLink(created.getId(), true, null));
    }

    /** A shopping list item was unchecked: undoes {@link #addFromShopping}, if the pantry item still exists. */
    public void revertFromShopping(@NotNull CurrentMember currentMember, @NotNull PantryLink link) {
        pantry.findByIdAndHouseholdId(link.pantryItemId(), currentMember.householdId()).ifPresent(item -> {
            if (link.created()) {
                pantry.deleteByHouseholdIdAndId(currentMember.householdId(), item.getId());
            } else {
                item.restoreQuantity(link.previousQuantity());
            }
        });
    }

    private static void checkNoDuplicates(PantryUpdate update) {
        Set<String> seen = new HashSet<>();
        List<String> names = new ArrayList<>();
        update.upsert().forEach(item -> names.add(item.name()));
        names.addAll(update.remove());
        for (String name : names) {
            if (!seen.add(name.strip().toLowerCase(Locale.ROOT))) {
                throw new InvalidInputException("\"" + name.strip()
                        + "\" appears more than once in upsert and remove: name each item once.");
            }
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
