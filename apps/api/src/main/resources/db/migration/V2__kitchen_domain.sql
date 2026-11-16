-- Kitchen domain: appliances, recipes, sharing, menus, shopping lists, pantry, PDF exports.
--
-- Isolation at the database level: a row of household A can never reference a row of
-- household B. Every household table declares unique (household_id, id), and references between
-- household tables are composite foreign keys on (household_id, <ref>_id). Even a bug in a
-- service cannot link a menu of household A to a recipe of household B: PostgreSQL rejects it.
--
-- Aisles (docs/PLAN.md §5): produce, frozen, meat_fish, dairy, bakery, dry_goods, canned,
-- condiments, drinks, household, other.

-- Appliances ------------------------------------------------------------------------------------

create table appliance (
    id               uuid         primary key,
    household_id     uuid         not null references household (id) on delete cascade,
    name             varchar(100) not null,
    brand_model      varchar(200),
    -- Confirmed spec sheet: {modes: [{name, temp_min, temp_max, notes}], capacity, remarks}.
    spec             jsonb        not null check (jsonb_typeof(spec) = 'object'),
    usage_preference varchar(10)  not null default 'suggest'
                                  check (usage_preference in ('default', 'suggest', 'on_request')),
    confirmed_at     timestamptz,
    confirmed_by     uuid         references app_user (id) on delete set null,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    unique (household_id, id)
);

create index appliance_household_idx on appliance (household_id);

-- Sharing between households ----------------------------------------------------------------------
-- Not household-scoped for reading: published recipes are visible to every household by design.
-- Only the origin household can withdraw them. Content is an immutable snapshot, always treated
-- as third-party data (CLAUDE.md rule 6).

create table shared_recipe (
    id                  uuid         primary key,
    origin_household_id uuid         not null references household (id) on delete cascade,
    source_recipe_id    uuid,
    title               varchar(200) not null,
    language            varchar(2)   not null check (language in ('fr', 'nl', 'en')),
    content             jsonb        not null check (jsonb_typeof(content) = 'object'),
    published_by        uuid         references app_user (id) on delete set null,
    published_at        timestamptz  not null default now(),
    withdrawn_at        timestamptz
);

create index shared_recipe_origin_idx on shared_recipe (origin_household_id);

-- Recipes ---------------------------------------------------------------------------------------

create table recipe (
    id                 uuid          primary key,
    household_id       uuid          not null references household (id) on delete cascade,
    -- Null = classic recipe (pan, pot, oven).
    appliance_id       uuid,
    author_id          uuid          references app_user (id) on delete set null,
    title              varchar(200)  not null,
    summary            varchar(1000),
    servings           smallint      not null check (servings between 1 and 50),
    active_minutes     smallint      check (active_minutes between 0 and 1440),
    total_minutes      smallint      check (total_minutes between 0 and 10080),
    -- [{name, quantity, unit, aisle, note}]
    ingredients        jsonb         not null check (jsonb_typeof(ingredients) = 'array'),
    -- [{text, duration_min, appliance_mode, temperature}]
    steps              jsonb         not null check (jsonb_typeof(steps) = 'array'),
    tags               jsonb         not null default '[]' check (jsonb_typeof(tags) = 'array'),
    nutrition_estimate jsonb         check (jsonb_typeof(nutrition_estimate) = 'object'),
    benefits           jsonb         not null default '[]' check (jsonb_typeof(benefits) = 'array'),
    language           varchar(2)    not null check (language in ('fr', 'nl', 'en')),
    version            integer       not null default 1 check (version >= 1),
    source             varchar(500),
    shared_recipe_id   uuid          references shared_recipe (id) on delete set null,
    created_at         timestamptz   not null default now(),
    updated_at         timestamptz   not null default now(),
    unique (household_id, id),
    -- Deleting an appliance turns its recipes into classic recipes instead of deleting them.
    foreign key (household_id, appliance_id)
        references appliance (household_id, id) on delete set null (appliance_id)
);

create index recipe_household_idx on recipe (household_id);

alter table shared_recipe
    add constraint shared_recipe_source_fk foreign key (source_recipe_id)
        references recipe (id) on delete set null;

-- Personal rating of a household recipe. Composite key to household_member: when someone leaves
-- the household, their ratings leave with them (docs/SCOPE.md).
create table recipe_rating (
    id           uuid          primary key,
    household_id uuid          not null,
    recipe_id    uuid          not null,
    user_id      uuid          not null,
    score        smallint      not null check (score between 1 and 10),
    comment      varchar(2000),
    adjustments  varchar(2000),
    cooked_at    timestamptz   not null default now(),
    foreign key (household_id, recipe_id) references recipe (household_id, id) on delete cascade,
    foreign key (household_id, user_id)
        references household_member (household_id, user_id) on delete cascade
);

create index recipe_rating_recipe_idx on recipe_rating (household_id, recipe_id);
create index recipe_rating_user_idx on recipe_rating (household_id, user_id);

-- Menus -----------------------------------------------------------------------------------------

create table menu (
    id                  uuid         primary key,
    household_id        uuid         not null references household (id) on delete cascade,
    title               varchar(200) not null,
    starts_on           date         not null,
    ends_on             date         not null,
    -- {required: [appliance_id], excluded: [appliance_id]} for this period only.
    appliance_overrides jsonb        not null default '{}'
                                     check (jsonb_typeof(appliance_overrides) = 'object'),
    created_by          uuid         references app_user (id) on delete set null,
    created_at          timestamptz  not null default now(),
    unique (household_id, id),
    check (ends_on >= starts_on and ends_on - starts_on <= 31)
);

create index menu_household_idx on menu (household_id, starts_on desc);

create table menu_item (
    id           uuid        primary key,
    household_id uuid        not null,
    menu_id      uuid        not null,
    served_on    date        not null,
    meal         varchar(10) not null check (meal in ('breakfast', 'lunch', 'dinner', 'snack')),
    recipe_id    uuid        not null,
    servings     smallint    not null check (servings between 1 and 50),
    foreign key (household_id, menu_id) references menu (household_id, id) on delete cascade,
    -- No cascade: a recipe used in a menu cannot be deleted silently (the service decides).
    -- Deferred to the end of the transaction, so that deleting a whole household (which cascades
    -- to both recipes and menu items, in no guaranteed order) still succeeds.
    foreign key (household_id, recipe_id) references recipe (household_id, id)
        deferrable initially deferred
);

create index menu_item_menu_idx on menu_item (household_id, menu_id);
create index menu_item_recipe_idx on menu_item (household_id, recipe_id);

-- Shopping lists --------------------------------------------------------------------------------

create table shopping_list (
    id           uuid         primary key,
    household_id uuid         not null references household (id) on delete cascade,
    menu_id      uuid,
    title        varchar(200) not null,
    created_by   uuid         references app_user (id) on delete set null,
    created_at   timestamptz  not null default now(),
    unique (household_id, id),
    foreign key (household_id, menu_id)
        references menu (household_id, id) on delete set null (menu_id)
);

create index shopping_list_household_idx on shopping_list (household_id, created_at desc);

create table shopping_list_item (
    id           uuid          primary key,
    household_id uuid          not null,
    list_id      uuid          not null,
    name         varchar(200)  not null,
    quantity     numeric(10, 3) check (quantity > 0),
    unit         varchar(30),
    aisle        varchar(20)   not null default 'other'
                               check (aisle in ('produce', 'frozen', 'meat_fish', 'dairy', 'bakery',
                                                'dry_goods', 'canned', 'condiments', 'drinks',
                                                'household', 'other')),
    position     integer       not null default 0,
    checked      boolean       not null default false,
    checked_by   uuid          references app_user (id) on delete set null,
    checked_at   timestamptz,
    foreign key (household_id, list_id)
        references shopping_list (household_id, id) on delete cascade
);

create index shopping_list_item_list_idx on shopping_list_item (household_id, list_id, position);

-- Pantry ----------------------------------------------------------------------------------------

create table pantry_item (
    id           uuid         primary key,
    household_id uuid         not null references household (id) on delete cascade,
    name         varchar(200) not null,
    -- Approximate, free text ("half a bag", "about 500 g").
    quantity     varchar(100),
    aisle        varchar(20)  not null default 'other'
                              check (aisle in ('produce', 'frozen', 'meat_fish', 'dairy', 'bakery',
                                               'dry_goods', 'canned', 'condiments', 'drinks',
                                               'household', 'other')),
    source       varchar(10)  not null check (source in ('shopping', 'manual')),
    updated_at   timestamptz  not null default now()
);

create index pantry_item_household_idx on pantry_item (household_id);

-- PDF exports -----------------------------------------------------------------------------------

create table pdf_export (
    id           uuid        primary key,
    household_id uuid        not null references household (id) on delete cascade,
    menu_id      uuid        not null,
    requested_by uuid        references app_user (id) on delete set null,
    -- SHA-256 of the download token, never the token itself.
    token_hash   bytea       not null unique check (octet_length(token_hash) = 32),
    language     varchar(2)  not null check (language in ('fr', 'nl', 'en')),
    created_at   timestamptz not null default now(),
    expires_at   timestamptz not null,
    foreign key (household_id, menu_id) references menu (household_id, id) on delete cascade
);

create index pdf_export_expires_idx on pdf_export (expires_at);
