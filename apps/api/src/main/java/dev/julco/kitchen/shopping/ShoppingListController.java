package dev.julco.kitchen.shopping;

import dev.julco.kitchen.household.CurrentMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Site API for the shopping list fallback page (/list/:id), for AI apps that cannot display the
 * widget. Same service as the MCP tools; {@link CurrentMember} comes from the browser session.
 * A list of another household is a 404, like a list that does not exist.
 */
@RestController
@RequestMapping("/api/shopping-lists")
class ShoppingListController {

    private final ShoppingListService lists;

    ShoppingListController(ShoppingListService lists) {
        this.lists = lists;
    }

    @GetMapping("/{listId}")
    ShoppingListView get(CurrentMember currentMember, @PathVariable UUID listId) {
        return lists.get(currentMember, listId);
    }

    @PostMapping("/{listId}/check")
    ShoppingListView check(CurrentMember currentMember, @PathVariable UUID listId, @Valid @RequestBody ItemsRequest request) {
        return lists.check(currentMember, listId, request.itemIds());
    }

    @PostMapping("/{listId}/uncheck")
    ShoppingListView uncheck(CurrentMember currentMember, @PathVariable UUID listId, @Valid @RequestBody ItemsRequest request) {
        return lists.uncheck(currentMember, listId, request.itemIds());
    }

    record ItemsRequest(@NotNull @Size(min = 1, max = ShoppingListService.MAX_ITEMS) List<@NotNull UUID> itemIds) {}
}
