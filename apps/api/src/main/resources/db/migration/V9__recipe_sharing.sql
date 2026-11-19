-- Phase 11: sharing recipes between households.

-- Search among published recipes, like in a household's own book (V6): title first, then tags and
-- ingredient names, then the summary; accent-insensitive, stemmed in the recipe's language.
alter table shared_recipe
    add column search_vector tsvector generated always as (
        setweight(to_tsvector(kitchen_search_config(language), kitchen_unaccent(title)), 'A')
        || setweight(to_tsvector(kitchen_search_config(language), kitchen_unaccent(
               coalesce(content -> 'tags', '[]'::jsonb)::text || ' '
               || jsonb_path_query_array(content, '$.ingredients[*].name')::text)), 'B')
        || setweight(to_tsvector(kitchen_search_config(language), kitchen_unaccent(
               coalesce(content ->> 'summary', ''))), 'C')
    ) stored;

create index shared_recipe_search_idx on shared_recipe using gin (search_vector);

-- One live publication per recipe: publishing again withdraws the previous snapshot first.
create unique index shared_recipe_one_live_idx on shared_recipe (source_recipe_id)
    where withdrawn_at is null;

-- The copies of a shared recipe in other books, for its average rating and to import only once.
create index recipe_shared_recipe_idx on recipe (shared_recipe_id) where shared_recipe_id is not null;
