-- Phase 7: full-text search in the household's recipe book, and the recipe version each rating
-- refers to.

-- Accent-insensitive search ("creme brulee" finds "crème brûlée"). unaccent ships with the
-- official PostgreSQL image and is a trusted extension: the database owner can create it,
-- no superuser needed.
create extension if not exists unaccent;

-- unaccent() is only STABLE (its rules file could change), and PostgreSQL only accepts IMMUTABLE
-- functions in a generated column. This wrapper names its dictionary explicitly and is declared
-- IMMUTABLE, the usual pattern: if the unaccent rules ever changed, the column would have to be
-- recomputed.
create function kitchen_unaccent(value text) returns text
    language sql immutable parallel safe strict
    return public.unaccent('public.unaccent'::regdictionary, value);

-- Stemming follows the recipe's own language: "tomates" finds "tomate" in French.
create function kitchen_search_config(language varchar) returns regconfig
    language sql immutable parallel safe strict
    return case language
        when 'fr' then 'french'::regconfig
        when 'nl' then 'dutch'::regconfig
        else 'english'::regconfig
    end;

-- Computed by PostgreSQL on every write, never by the application. Weights: title first, then
-- tags and ingredient names, then the summary.
alter table recipe
    add column search_vector tsvector generated always as (
        setweight(to_tsvector(kitchen_search_config(language), kitchen_unaccent(title)), 'A')
        || setweight(to_tsvector(kitchen_search_config(language), kitchen_unaccent(
               tags::text || ' ' || jsonb_path_query_array(ingredients, '$[*].name')::text)), 'B')
        || setweight(to_tsvector(kitchen_search_config(language), kitchen_unaccent(coalesce(summary, ''))), 'C')
    ) stored;

create index recipe_search_idx on recipe using gin (search_vector);

-- A rating is about the recipe as it was cooked: after an edit, an old "6/10" stays attached to
-- the version it judged. Existing ratings (none in production yet) refer to version 1.
alter table recipe_rating
    add column recipe_version integer not null default 1 check (recipe_version >= 1);

-- Search by "not cooked for X days" and by latest rating per person.
create index recipe_rating_latest_idx on recipe_rating (household_id, recipe_id, user_id, cooked_at desc);
