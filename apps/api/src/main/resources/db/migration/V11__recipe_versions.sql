-- Recipe history: every saved version of a recipe is kept as a complete copy, so that the
-- household can go back to an earlier one ("the rapeseed oil was a bad idea"). Ratings already
-- record the version they judged (V6), so each version can show how it was rated.

create table recipe_version (
    id           uuid         primary key,
    household_id uuid         not null,
    recipe_id    uuid         not null,
    version      integer      not null check (version >= 1),
    -- The recipe's editable fields as they were, in snake_case like the other stored documents:
    -- {appliance_id, title, summary, servings, active_minutes, total_minutes, ingredients, steps,
    --  tags, nutrition_estimate, benefits, language, source}
    content      jsonb        not null check (jsonb_typeof(content) = 'object'),
    -- What changed, in the words of the person or their AI ("olive oil replaced by rapeseed oil").
    change_note  varchar(300),
    created_by   uuid         references app_user (id) on delete set null,
    created_at   timestamptz  not null default now(),
    unique (household_id, recipe_id, version),
    -- Composite key: a version always belongs to a recipe of the same household. The history goes
    -- with the recipe when it is deleted.
    foreign key (household_id, recipe_id) references recipe (household_id, id) on delete cascade
);

-- Existing recipes start their history with their current state: earlier versions were not kept
-- before this migration. Random (v4) ids here; the application generates v7 ids.
insert into recipe_version (id, household_id, recipe_id, version, content, created_by, created_at)
select gen_random_uuid(), household_id, id, version,
       jsonb_build_object(
           'appliance_id', appliance_id,
           'title', title,
           'summary', summary,
           'servings', servings,
           'active_minutes', active_minutes,
           'total_minutes', total_minutes,
           'ingredients', ingredients,
           'steps', steps,
           'tags', tags,
           'nutrition_estimate', nutrition_estimate,
           'benefits', benefits,
           'language', language,
           'source', source),
       author_id, updated_at
from recipe;
