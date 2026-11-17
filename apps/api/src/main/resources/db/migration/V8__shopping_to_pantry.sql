-- Phase 9: checking a shopping list item puts it in the pantry; unchecking it undoes exactly that.
-- The item remembers which pantry item the check created or updated, and what it replaced.

alter table shopping_list_item
    add column pantry_item_id           uuid,
    -- true: the check created the pantry item (unchecking deletes it);
    -- false: it updated an existing one (unchecking restores the previous quantity).
    add column pantry_item_created      boolean,
    add column pantry_previous_quantity varchar(100),
    -- Composite key (unique (household_id, id) of V7): a list item can only point to a pantry item
    -- of its own household. If the pantry item is removed, the link is simply forgotten.
    add constraint shopping_list_item_pantry_item_fk
        foreign key (household_id, pantry_item_id)
        references pantry_item (household_id, id) on delete set null (pantry_item_id),
    add constraint shopping_list_item_pantry_link_check
        check (pantry_item_id is null or (checked and pantry_item_created is not null));
