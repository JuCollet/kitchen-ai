-- OAuth authorization server for AI clients (Claude, ChatGPT, any MCP client).
--
-- Schemas expected by Spring Security's JDBC services, copied from
-- spring-security-oauth2-authorization-server 7.1.2 with the PostgreSQL changes the files ask for:
-- 'blob' becomes 'text', 'timestamp' becomes 'timestamptz'. Column names and types must stay as
-- Spring Security reads and writes them.
--
-- principal_name is the Kitchen user id (the authenticated principal name). Our addition, as for
-- passkeys: a generated app_user_id column with a foreign key, so that deleting an account deletes
-- its authorizations, which revokes the tokens of every AI it connected.

-- AI clients, registered dynamically (RFC 7591) when someone adds Kitchen to their AI.
create table oauth2_registered_client (
    id                            varchar(100)  not null,
    client_id                     varchar(100)  not null unique,
    client_id_issued_at           timestamptz   default current_timestamp not null,
    client_secret                 varchar(200)  default null,
    client_secret_expires_at      timestamptz   default null,
    client_name                   varchar(200)  not null,
    client_authentication_methods varchar(1000) not null,
    authorization_grant_types     varchar(1000) not null,
    redirect_uris                 varchar(1000) default null,
    post_logout_redirect_uris     varchar(1000) default null,
    scopes                        varchar(1000) not null,
    client_settings               varchar(2000) not null,
    token_settings                varchar(2000) not null,
    primary key (id)
);

-- Authorization codes, access tokens and refresh tokens issued to an AI client for a person.
create table oauth2_authorization (
    id                            varchar(100)  not null,
    registered_client_id          varchar(100)  not null
                                                references oauth2_registered_client (id) on delete cascade,
    principal_name                varchar(200)  not null,
    authorization_grant_type      varchar(100)  not null,
    authorized_scopes             varchar(1000) default null,
    attributes                    text          default null,
    state                         varchar(500)  default null,
    authorization_code_value      text          default null,
    authorization_code_issued_at  timestamptz   default null,
    authorization_code_expires_at timestamptz   default null,
    authorization_code_metadata   text          default null,
    access_token_value            text          default null,
    access_token_issued_at        timestamptz   default null,
    access_token_expires_at       timestamptz   default null,
    access_token_metadata         text          default null,
    access_token_type             varchar(100)  default null,
    access_token_scopes           varchar(1000) default null,
    oidc_id_token_value           text          default null,
    oidc_id_token_issued_at       timestamptz   default null,
    oidc_id_token_expires_at      timestamptz   default null,
    oidc_id_token_metadata        text          default null,
    refresh_token_value           text          default null,
    refresh_token_issued_at       timestamptz   default null,
    refresh_token_expires_at      timestamptz   default null,
    refresh_token_metadata        text          default null,
    user_code_value               text          default null,
    user_code_issued_at           timestamptz   default null,
    user_code_expires_at          timestamptz   default null,
    user_code_metadata            text          default null,
    device_code_value             text          default null,
    device_code_issued_at         timestamptz   default null,
    device_code_expires_at        timestamptz   default null,
    device_code_metadata          text          default null,
    -- Our addition. Null when the principal is not a Kitchen user (never the case: only the
    -- authorization code flow is allowed), so that an unexpected value cannot break an insert.
    app_user_id                   uuid          generated always as (
                                      case when principal_name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                                           then principal_name::uuid end) stored
                                                references app_user (id) on delete cascade,
    primary key (id)
);

create index oauth2_authorization_client_idx on oauth2_authorization (registered_client_id);
create index oauth2_authorization_user_idx on oauth2_authorization (app_user_id);

-- Consent given by a person to an AI client.
create table oauth2_authorization_consent (
    registered_client_id varchar(100)  not null
                                       references oauth2_registered_client (id) on delete cascade,
    principal_name       varchar(200)  not null,
    authorities          varchar(1000) not null,
    app_user_id          uuid          generated always as (
                             case when principal_name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                                  then principal_name::uuid end) stored
                                       references app_user (id) on delete cascade,
    primary key (registered_client_id, principal_name)
);
