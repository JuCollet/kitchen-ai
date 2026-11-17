-- Phase 8: menus and pantry.

-- One pantry line per ingredient and household: update_pantry adds or updates by name, so
-- "Rice" and "rice" must be the same line. lower() rather than citext: no extension needed,
-- and the service compares names the same way.
create unique index pantry_item_name_idx on pantry_item (household_id, lower(name));

-- Same pattern as every other household table (V2), so that a future table can reference a
-- pantry item with a composite foreign key.
alter table pantry_item add constraint pantry_item_household_id_id_key unique (household_id, id);

-- "Recipes to rate" looks for the meals served recently in the household's menus.
create index menu_item_served_idx on menu_item (household_id, served_on);
