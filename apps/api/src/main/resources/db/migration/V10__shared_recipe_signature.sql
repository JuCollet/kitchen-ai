-- Phase 11: a publication may be signed, by choice of the person who publishes it, so that friends
-- can find the recipes of a given household or person. Anonymous (null) by default.
-- Only the choice is stored, never a copy of the name: the name shown is read when the recipe is
-- read (household name, or the person's display name), so a renamed household or a deleted
-- account (published_by set to null) never leaves a stale or orphaned name behind.
alter table shared_recipe
    add column signature varchar(10) check (signature in ('household', 'person'));
