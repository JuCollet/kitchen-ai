package dev.julco.kitchen.shopping;

import dev.julco.kitchen.common.Aisle;
import dev.julco.kitchen.common.InvalidInputException;
import dev.julco.kitchen.common.NotFoundException;
import dev.julco.kitchen.household.CurrentMember;
import dev.julco.kitchen.household.HouseholdService;
import dev.julco.kitchen.household.MemberSummary;
import dev.julco.kitchen.menu.MenuService;
import dev.julco.kitchen.pantry.PantryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * The household's shopping lists, shared by its members and their AIs. A checked item goes into
 * the pantry; unchecking it undoes exactly what the check did. Every read and write is filtered by
 * the current member's household (CLAUDE.md rule 1).
 */
@Service
@Validated
@Transactional
public class ShoppingListService {

    static final int MAX_ITEMS = 200;

    private final ShoppingListRepository lists;
    private final ShoppingListItemRepository items;
    private final MenuService menus;
    private final PantryService pantry;
    private final HouseholdService households;

    public ShoppingListService(
            ShoppingListRepository lists,
            ShoppingListItemRepository items,
            MenuService menus,
            PantryService pantry,
            HouseholdService households) {
        this.lists = lists;
        this.items = items;
        this.menus = menus;
        this.pantry = pantry;
        this.households = households;
    }

    public ShoppingListView create(@NotNull CurrentMember currentMember, @NotNull @Valid ShoppingListInput input) {
        if (input.menuId() != null) {
            try {
                menus.get(currentMember, input.menuId());
            } catch (NotFoundException e) {
                throw new InvalidInputException(
                        "Menu not found in this household: use the menu id from get_context or get_menu.");
            }
        }
        ShoppingList list = lists.save(new ShoppingList(
                currentMember.householdId(), input.menuId(), input.title().strip(), currentMember.userId()));
        addItems(currentMember, list, input.items(), 0);
        return view(currentMember, list);
    }

    @Transactional(readOnly = true)
    public ShoppingListView get(@NotNull CurrentMember currentMember, @NotNull UUID listId) {
        return view(currentMember, find(currentMember, listId));
    }

    /** The most recently created list. */
    @Transactional(readOnly = true)
    public Optional<ShoppingListView> latest(@NotNull CurrentMember currentMember) {
        return lists.findFirstByHouseholdIdOrderByCreatedAtDesc(currentMember.householdId())
                .map(list -> view(currentMember, list));
    }

    @Transactional(readOnly = true)
    public Optional<ShoppingListSummary> latestSummary(@NotNull CurrentMember currentMember) {
        return latest(currentMember)
                .map(list -> new ShoppingListSummary(list.id(), list.title(), list.itemCount(), list.remaining()));
    }

    /** Renames, then removes, edits and adds items. Item ids must belong to this list. */
    public ShoppingListView update(@NotNull CurrentMember currentMember, @NotNull @Valid ShoppingListUpdate update) {
        ShoppingList list = find(currentMember, update.listId());
        if (update.title() != null) {
            if (update.title().isBlank()) {
                throw new InvalidInputException("title cannot be blank.");
            }
            list.rename(update.title().strip());
        }
        Map<UUID, ShoppingListItem> current = itemsById(currentMember, list);
        Set<UUID> removed = new LinkedHashSet<>(update.remove());
        for (UUID id : removed) {
            ShoppingListItem item = existing(current, id, "remove");
            // A checked item already went into the pantry: removing it from the list undoes that too.
            item.pantryLink().ifPresent(link -> pantry.revertFromShopping(currentMember, link));
            items.deleteByHouseholdIdAndId(currentMember.householdId(), item.getId());
        }
        for (ShoppingItemEdit edit : update.edit()) {
            if (removed.contains(edit.itemId())) {
                throw new InvalidInputException("Item " + edit.itemId() + " is both removed and edited.");
            }
            if (edit.name() != null && edit.name().isBlank()) {
                throw new InvalidInputException("edit: the name of item " + edit.itemId() + " cannot be blank.");
            }
            existing(current, edit.itemId(), "edit").edit(
                    edit.name() == null ? null : edit.name().strip(),
                    edit.quantity(),
                    blankToNull(edit.unit()),
                    edit.aisle() == null ? null : Aisle.fromCode(edit.aisle()));
        }
        if (current.size() - removed.size() + update.add().size() > MAX_ITEMS) {
            throw new InvalidInputException("A shopping list holds at most " + MAX_ITEMS + " items.");
        }
        int nextPosition = current.values().stream().mapToInt(ShoppingListItem::getPosition).max().orElse(-1) + 1;
        addItems(currentMember, list, update.add(), nextPosition);
        return view(currentMember, list);
    }

    /**
     * Checks items (idempotent: an item already checked by someone else stays as it is) and puts
     * them in the pantry.
     */
    public ShoppingListView check(
            @NotNull CurrentMember currentMember,
            @NotNull UUID listId,
            @NotNull @Size(min = 1, max = MAX_ITEMS) List<@NotNull UUID> itemIds) {
        ShoppingList list = find(currentMember, listId);
        Map<UUID, ShoppingListItem> current = itemsById(currentMember, list);
        Instant now = Instant.now();
        for (UUID id : new LinkedHashSet<>(itemIds)) {
            ShoppingListItem item = existing(current, id, "check");
            if (!item.isChecked()) {
                item.check(currentMember.userId(), now, pantry.addFromShopping(
                        currentMember, item.getName(), quantityText(item), item.getAisle()).orElse(null));
            }
        }
        return view(currentMember, list);
    }

    /** Unchecks items and takes back out of the pantry what checking them put in. Idempotent too. */
    public ShoppingListView uncheck(
            @NotNull CurrentMember currentMember,
            @NotNull UUID listId,
            @NotNull @Size(min = 1, max = MAX_ITEMS) List<@NotNull UUID> itemIds) {
        ShoppingList list = find(currentMember, listId);
        Map<UUID, ShoppingListItem> current = itemsById(currentMember, list);
        for (UUID id : new LinkedHashSet<>(itemIds)) {
            ShoppingListItem item = existing(current, id, "uncheck");
            if (item.isChecked()) {
                item.pantryLink().ifPresent(link -> pantry.revertFromShopping(currentMember, link));
                item.uncheck();
            }
        }
        return view(currentMember, list);
    }

    private ShoppingList find(CurrentMember currentMember, UUID listId) {
        return lists.findByIdAndHouseholdId(listId, currentMember.householdId()).orElseThrow(NotFoundException::new);
    }

    private Map<UUID, ShoppingListItem> itemsById(CurrentMember currentMember, ShoppingList list) {
        return items.findAllByHouseholdIdAndListIdOrderByPosition(currentMember.householdId(), list.getId()).stream()
                .collect(Collectors.toMap(ShoppingListItem::getId, Function.identity()));
    }

    /** Only ids read from this very list are accepted: an item of another list (or household) is unknown. */
    private static ShoppingListItem existing(Map<UUID, ShoppingListItem> current, UUID id, String operation) {
        ShoppingListItem item = current.get(id);
        if (item == null) {
            throw new InvalidInputException(operation + ": item " + id
                    + " is not in this shopping list. Use get_shopping_list to get the item ids.");
        }
        return item;
    }

    private void addItems(CurrentMember currentMember, ShoppingList list, List<ShoppingItemInput> input, int firstPosition) {
        int position = firstPosition;
        for (ShoppingItemInput item : input) {
            items.save(new ShoppingListItem(
                    currentMember.householdId(),
                    list.getId(),
                    item.name().strip(),
                    item.quantity(),
                    blankToNull(item.unit()),
                    item.aisle() == null ? Aisle.OTHER : Aisle.fromCode(item.aisle()),
                    position++));
        }
    }

    private ShoppingListView view(CurrentMember currentMember, ShoppingList list) {
        Map<UUID, String> names = households.listMembers(currentMember).stream()
                .collect(Collectors.toMap(MemberSummary::userId, MemberSummary::displayName));
        List<ShoppingListView.Item> views = items
                .findAllByHouseholdIdAndListIdOrderByPosition(currentMember.householdId(), list.getId())
                .stream()
                .sorted(Comparator.comparing(ShoppingListItem::getAisle).thenComparing(ShoppingListItem::getPosition))
                .map(item -> new ShoppingListView.Item(
                        item.getId(),
                        item.getName(),
                        item.getQuantity(),
                        item.getUnit(),
                        item.getAisle(),
                        item.isChecked(),
                        item.getCheckedBy() == null ? null : names.get(item.getCheckedBy()),
                        item.getCheckedAt()))
                .toList();
        int remaining = (int) views.stream().filter(item -> !item.checked()).count();
        return new ShoppingListView(
                list.getId(), list.getTitle(), list.getMenuId(), list.getCreatedAt(), views.size(), remaining, views);
    }

    /** "600 g", "2", or null when the list gives no quantity (the pantry then keeps its own). */
    private static String quantityText(ShoppingListItem item) {
        BigDecimal quantity = item.getQuantity();
        if (quantity == null) {
            return null;
        }
        String number = quantity.stripTrailingZeros().toPlainString();
        return item.getUnit() == null ? number : number + " " + item.getUnit();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
