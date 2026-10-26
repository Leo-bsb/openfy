-- ============================================================================
-- Openfy — schema inicial Supabase
-- Rode UMA VEZ no SQL Editor do projeto (role postgres).
--
-- Decisões do v1:
--   * conta opcional (modo local continua no app)
--   * dono gerencia membros; todos os membros são editores
--   * sem faixa duplicada na mesma playlist (unique playlist_id + track_id)
--   * soft delete + server_rev para sync incremental
--   * handoff simples: um snapshot playback_state por usuário
--   * social v1: perfis, follows, playlists públicas e activities
--   * capas em bucket público (metadados de playlist, sem áudio)
-- ============================================================================

create sequence if not exists public.openfy_rev_seq;

-- ----------------------------------------------------------------------------
-- 1. Tabelas
-- ----------------------------------------------------------------------------

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    handle text not null unique,
    display_name text not null default '',
    avatar_url text not null default '',
    is_public boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    server_rev bigint not null
);

create table if not exists public.devices (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid() references public.profiles(id) on delete cascade,
    name text not null default 'Dispositivo',
    platform text not null default 'other' check (platform in ('windows', 'android', 'other')),
    last_seen_at timestamptz not null default now(),
    push_token text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.tracks (
    id text primary key,
    title text not null,
    artist text not null,
    album text not null default '',
    duration int not null default 0,
    thumbnail text not null default '',
    updated_at timestamptz not null default now(),
    server_rev bigint not null
);

create table if not exists public.playlists (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null default auth.uid() references public.profiles(id) on delete cascade,
    name text not null,
    description text not null default '',
    cover_url text not null default '',
    visibility text not null default 'private' check (visibility in ('private', 'public')),
    share_code text not null unique default substr(md5(random()::text || clock_timestamp()::text), 1, 12),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false,
    server_rev bigint not null
);

create table if not exists public.playlist_members (
    playlist_id uuid not null references public.playlists(id) on delete cascade,
    user_id uuid not null default auth.uid() references public.profiles(id) on delete cascade,
    role text not null default 'editor' check (role in ('owner', 'editor', 'viewer')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false,
    server_rev bigint not null,
    primary key (playlist_id, user_id)
);

create table if not exists public.playlist_items (
    id uuid primary key default gen_random_uuid(),
    playlist_id uuid not null references public.playlists(id) on delete cascade,
    track_id text not null references public.tracks(id) on delete cascade,
    rank text not null check (rank <> ''),
    added_by uuid default auth.uid() references public.profiles(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false,
    server_rev bigint not null,
    unique (playlist_id, track_id)
);

create table if not exists public.likes (
    user_id uuid not null default auth.uid() references public.profiles(id) on delete cascade,
    track_id text not null references public.tracks(id) on delete cascade,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false,
    server_rev bigint not null,
    primary key (user_id, track_id)
);

create table if not exists public.play_events (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid() references public.profiles(id) on delete cascade,
    device_id uuid references public.devices(id) on delete set null,
    track_id text not null references public.tracks(id) on delete cascade,
    started_at timestamptz not null default now(),
    played_ms int not null default 0 check (played_ms >= 0),
    duration_ms int check (duration_ms is null or duration_ms >= 0),
    completed boolean not null default false,
    skipped boolean not null default false,
    source text not null default 'unknown',
    context_id text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false,
    server_rev bigint not null
);

create table if not exists public.playback_state (
    user_id uuid primary key default auth.uid() references public.profiles(id) on delete cascade,
    device_id uuid references public.devices(id) on delete set null,
    track_id text references public.tracks(id) on delete set null,
    queue jsonb not null default '[]'::jsonb,
    queue_index int not null default -1,
    position_ms int not null default 0 check (position_ms >= 0),
    playing boolean not null default false,
    shuffle boolean not null default false,
    repeat text not null default 'OFF' check (repeat in ('OFF', 'ALL', 'ONE')),
    updated_at timestamptz not null default now(),
    server_rev bigint not null
);

create table if not exists public.follows (
    follower_id uuid not null default auth.uid() references public.profiles(id) on delete cascade,
    followed_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false,
    server_rev bigint not null,
    primary key (follower_id, followed_id),
    check (follower_id <> followed_id)
);

create table if not exists public.activities (
    id uuid primary key default gen_random_uuid(),
    actor_id uuid not null default auth.uid() references public.profiles(id) on delete cascade,
    type text not null,
    playlist_id uuid references public.playlists(id) on delete set null,
    track_id text references public.tracks(id) on delete set null,
    payload jsonb not null default '{}'::jsonb,
    is_public boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted boolean not null default false,
    server_rev bigint not null
);

-- ----------------------------------------------------------------------------
-- 2. Revisão de servidor / updated_at
-- ----------------------------------------------------------------------------

create or replace function public.openfy_touch()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    new.updated_at := now();
    new.server_rev := nextval('public.openfy_rev_seq');
    return new;
end;
$$;

create or replace function public.openfy_touch_updated()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists openfy_touch on public.profiles;
create trigger openfy_touch before insert or update on public.profiles
for each row execute function public.openfy_touch();

drop trigger if exists openfy_touch on public.devices;
create trigger openfy_touch before insert or update on public.devices
for each row execute function public.openfy_touch_updated();

drop trigger if exists openfy_touch on public.tracks;
create trigger openfy_touch before insert or update on public.tracks
for each row execute function public.openfy_touch();

drop trigger if exists openfy_touch on public.playlists;
create trigger openfy_touch before insert or update on public.playlists
for each row execute function public.openfy_touch();

drop trigger if exists openfy_touch on public.playlist_members;
create trigger openfy_touch before insert or update on public.playlist_members
for each row execute function public.openfy_touch();

drop trigger if exists openfy_touch on public.playlist_items;
create trigger openfy_touch before insert or update on public.playlist_items
for each row execute function public.openfy_touch();

drop trigger if exists openfy_touch on public.likes;
create trigger openfy_touch before insert or update on public.likes
for each row execute function public.openfy_touch();

drop trigger if exists openfy_touch on public.play_events;
create trigger openfy_touch before insert or update on public.play_events
for each row execute function public.openfy_touch();

drop trigger if exists openfy_touch on public.playback_state;
create trigger openfy_touch before insert or update on public.playback_state
for each row execute function public.openfy_touch();

drop trigger if exists openfy_touch on public.follows;
create trigger openfy_touch before insert or update on public.follows
for each row execute function public.openfy_touch();

drop trigger if exists openfy_touch on public.activities;
create trigger openfy_touch before insert or update on public.activities
for each row execute function public.openfy_touch();

-- ----------------------------------------------------------------------------
-- 3. Funções de autorização
-- ----------------------------------------------------------------------------

create or replace function public.is_playlist_member(p_playlist uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.playlist_members
        where playlist_id = p_playlist
          and user_id = auth.uid()
          and deleted = false
    );
$$;

create or replace function public.is_playlist_owner(p_playlist uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.playlists
        where id = p_playlist
          and owner_id = auth.uid()
          and deleted = false
    );
$$;

create or replace function public.is_public_playlist(p_playlist uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.playlists
        where id = p_playlist
          and visibility = 'public'
          and deleted = false
    );
$$;

create or replace function public.is_following(p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.follows
        where follower_id = auth.uid()
          and followed_id = p_user
          and deleted = false
    );
$$;

-- ----------------------------------------------------------------------------
-- 4. Perfil automático no signup + dono automático de playlist
-- ----------------------------------------------------------------------------

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    base text;
    suffix text;
begin
    base := lower(regexp_replace(split_part(coalesce(new.email, 'user'), '@', 1), '[^a-z0-9_]', '', 'g'));
    if base = '' then
        base := 'user';
    end if;
    suffix := substr(md5(random()::text || new.id::text), 1, 4);

    insert into public.profiles (id, handle, display_name)
    values (
        new.id,
        base || '_' || suffix,
        split_part(coalesce(new.email, 'Openfy'), '@', 1)
    )
    on conflict (id) do nothing;

    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

-- Backfill para usuários que já existiam antes desta migration.
insert into public.profiles (id, handle, display_name)
select
    id,
    'user_' || replace(id::text, '-', ''),
    split_part(coalesce(email, 'Openfy'), '@', 1)
from auth.users
on conflict (id) do nothing;

create or replace function public.handle_new_playlist()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.playlist_members (playlist_id, user_id, role)
    values (new.id, new.owner_id, 'owner')
    on conflict (playlist_id, user_id) do update
        set role = 'owner', deleted = false;

    return new;
end;
$$;

drop trigger if exists on_playlist_created on public.playlists;
create trigger on_playlist_created
after insert on public.playlists
for each row execute function public.handle_new_playlist();

-- Editor pode mudar nome/descrição/capa; dono/visibilidade/código/exclusão ficam protegidos.
create or replace function public.protect_playlist_fields()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is not null and auth.uid() <> old.owner_id then
        new.owner_id := old.owner_id;
        new.visibility := old.visibility;
        new.share_code := old.share_code;
        new.deleted := old.deleted;
    end if;
    return new;
end;
$$;

drop trigger if exists protect_playlist_fields on public.playlists;
create trigger protect_playlist_fields
before update on public.playlists
for each row execute function public.protect_playlist_fields();

create or replace function public.protect_playlist_item_fields()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is not null then
        new.id := old.id;
        new.playlist_id := old.playlist_id;
        new.track_id := old.track_id;
        new.added_by := old.added_by;
        new.created_at := old.created_at;
    end if;
    return new;
end;
$$;

drop trigger if exists protect_playlist_item_fields on public.playlist_items;
create trigger protect_playlist_item_fields
before update on public.playlist_items
for each row execute function public.protect_playlist_item_fields();

create or replace function public.protect_like_fields()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is not null then
        new.user_id := old.user_id;
        new.track_id := old.track_id;
        new.created_at := old.created_at;
    end if;
    return new;
end;
$$;

drop trigger if exists protect_like_fields on public.likes;
create trigger protect_like_fields
before update on public.likes
for each row execute function public.protect_like_fields();

create or replace function public.protect_play_event_fields()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is not null then
        new.user_id := old.user_id;
        new.device_id := old.device_id;
        new.track_id := old.track_id;
        new.started_at := old.started_at;
        new.played_ms := old.played_ms;
        new.duration_ms := old.duration_ms;
        new.completed := old.completed;
        new.skipped := old.skipped;
        new.source := old.source;
        new.context_id := old.context_id;
        new.created_at := old.created_at;
    end if;
    return new;
end;
$$;

drop trigger if exists protect_play_event_fields on public.play_events;
create trigger protect_play_event_fields
before update on public.play_events
for each row execute function public.protect_play_event_fields();

create or replace function public.protect_activity_fields()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is not null then
        new.actor_id := old.actor_id;
        new.type := old.type;
        new.playlist_id := old.playlist_id;
        new.track_id := old.track_id;
        new.payload := old.payload;
        new.is_public := old.is_public;
        new.created_at := old.created_at;
    end if;
    return new;
end;
$$;

drop trigger if exists protect_activity_fields on public.activities;
create trigger protect_activity_fields
before update on public.activities
for each row execute function public.protect_activity_fields();

create or replace function public.protect_follow_fields()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is not null then
        new.follower_id := old.follower_id;
        new.followed_id := old.followed_id;
        new.created_at := old.created_at;
    end if;
    return new;
end;
$$;

drop trigger if exists protect_follow_fields on public.follows;
create trigger protect_follow_fields
before update on public.follows
for each row execute function public.protect_follow_fields();

create or replace function public.protect_member_fields()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is not null and auth.uid() = old.user_id then
        new.playlist_id := old.playlist_id;
        new.user_id := old.user_id;
        new.role := old.role;
        new.created_at := old.created_at;
    end if;
    return new;
end;
$$;

drop trigger if exists protect_member_fields on public.playlist_members;
create trigger protect_member_fields
before update on public.playlist_members
for each row execute function public.protect_member_fields();

-- ----------------------------------------------------------------------------
-- 5. RPCs de colaboração
-- ----------------------------------------------------------------------------

create or replace function public.join_playlist(p_code text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    pid uuid;
begin
    if auth.uid() is null then
        raise exception 'not_authenticated';
    end if;

    select id into pid
    from public.playlists
    where share_code = p_code
      and deleted = false;

    if pid is null then
        raise exception 'invalid_code';
    end if;

    insert into public.playlist_members (playlist_id, user_id, role)
    values (pid, auth.uid(), 'editor')
    on conflict (playlist_id, user_id) do update
        set deleted = false;

    return pid;
end;
$$;

create or replace function public.leave_playlist(p_playlist uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is null then
        raise exception 'not_authenticated';
    end if;

    if public.is_playlist_owner(p_playlist) then
        raise exception 'owner_cannot_leave';
    end if;

    update public.playlist_members
    set deleted = true
    where playlist_id = p_playlist
      and user_id = auth.uid();
end;
$$;

create or replace function public.remove_playlist_member(p_playlist uuid, p_user uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.is_playlist_owner(p_playlist) then
        raise exception 'not_owner';
    end if;

    if p_user = auth.uid() then
        raise exception 'cannot_remove_owner';
    end if;

    update public.playlist_members
    set deleted = true
    where playlist_id = p_playlist
      and user_id = p_user
      and role <> 'owner';
end;
$$;

create or replace function public.set_playlist_member_role(p_playlist uuid, p_user uuid, p_role text)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.is_playlist_owner(p_playlist) then
        raise exception 'not_owner';
    end if;
    if p_role not in ('editor', 'viewer') then
        raise exception 'invalid_role';
    end if;

    update public.playlist_members
    set role = p_role
    where playlist_id = p_playlist
      and user_id = p_user
      and role <> 'owner';
end;
$$;

create or replace function public.rotate_share_code(p_playlist uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    code text;
begin
    if not public.is_playlist_owner(p_playlist) then
        raise exception 'not_owner';
    end if;

    update public.playlists
    set share_code = substr(md5(random()::text || clock_timestamp()::text), 1, 12)
    where id = p_playlist
    returning share_code into code;

    return code;
end;
$$;

-- ----------------------------------------------------------------------------
-- 6. Índices
-- ----------------------------------------------------------------------------

create index if not exists playlists_owner_idx on public.playlists (owner_id);
create index if not exists playlists_public_idx on public.playlists (visibility) where deleted = false;
create index if not exists playlist_members_user_idx on public.playlist_members (user_id) where deleted = false;
create index if not exists playlist_items_playlist_rank_idx on public.playlist_items (playlist_id, rank) where deleted = false;
create index if not exists likes_user_idx on public.likes (user_id) where deleted = false;
create index if not exists play_events_user_started_idx on public.play_events (user_id, started_at desc);
create index if not exists activities_actor_created_idx on public.activities (actor_id, created_at desc);
create index if not exists follows_followed_idx on public.follows (followed_id) where deleted = false;

-- índices de sync
create index if not exists profiles_rev_idx on public.profiles (server_rev);
create index if not exists tracks_rev_idx on public.tracks (server_rev);
create index if not exists playlists_rev_idx on public.playlists (server_rev);
create index if not exists playlist_members_rev_idx on public.playlist_members (server_rev);
create index if not exists playlist_items_rev_idx on public.playlist_items (server_rev);
create index if not exists likes_rev_idx on public.likes (server_rev);
create index if not exists play_events_rev_idx on public.play_events (server_rev);
create index if not exists playback_state_rev_idx on public.playback_state (server_rev);
create index if not exists follows_rev_idx on public.follows (server_rev);
create index if not exists activities_rev_idx on public.activities (server_rev);

-- ----------------------------------------------------------------------------
-- 7. RLS
-- ----------------------------------------------------------------------------

alter table public.profiles enable row level security;
alter table public.devices enable row level security;
alter table public.tracks enable row level security;
alter table public.playlists enable row level security;
alter table public.playlist_members enable row level security;
alter table public.playlist_items enable row level security;
alter table public.likes enable row level security;
alter table public.play_events enable row level security;
alter table public.playback_state enable row level security;
alter table public.follows enable row level security;
alter table public.activities enable row level security;

-- profiles: leitura pública; cada um edita o próprio perfil
drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles
    for select using (true);

drop policy if exists profiles_insert_self on public.profiles;
create policy profiles_insert_self on public.profiles
    for insert to authenticated with check (id = auth.uid());

drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self on public.profiles
    for update to authenticated
    using (id = auth.uid())
    with check (id = auth.uid());

-- devices: só o dono
drop policy if exists devices_select on public.devices;
create policy devices_select on public.devices
    for select to authenticated using (user_id = auth.uid());

drop policy if exists devices_insert on public.devices;
create policy devices_insert on public.devices
    for insert to authenticated with check (user_id = auth.uid());

drop policy if exists devices_update on public.devices;
create policy devices_update on public.devices
    for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists devices_delete on public.devices;
create policy devices_delete on public.devices
    for delete to authenticated using (user_id = auth.uid());

-- tracks: metadados públicos; usuário autenticado pode inserir se não existir
drop policy if exists tracks_select on public.tracks;
create policy tracks_select on public.tracks
    for select using (true);

drop policy if exists tracks_insert on public.tracks;
create policy tracks_insert on public.tracks
    for insert to authenticated with check (true);

-- playlists: dono/membro ou pública
drop policy if exists playlists_select on public.playlists;
create policy playlists_select on public.playlists
    for select using (
        owner_id = auth.uid()
        or public.is_playlist_member(id)
        or (visibility = 'public' and deleted = false)
    );

drop policy if exists playlists_insert on public.playlists;
create policy playlists_insert on public.playlists
    for insert to authenticated with check (owner_id = auth.uid());

drop policy if exists playlists_update on public.playlists;
create policy playlists_update on public.playlists
    for update to authenticated
    using (owner_id = auth.uid() or public.is_playlist_member(id))
    with check (owner_id = auth.uid() or public.is_playlist_member(id));

-- membros: cada um vê o próprio vínculo ou os membros da playlist; dono altera
drop policy if exists playlist_members_select on public.playlist_members;
create policy playlist_members_select on public.playlist_members
    for select using (
        user_id = auth.uid()
        or public.is_playlist_member(playlist_id)
    );

drop policy if exists playlist_members_update on public.playlist_members;
create policy playlist_members_update on public.playlist_members
    for update to authenticated
    using (public.is_playlist_owner(playlist_id))
    with check (public.is_playlist_owner(playlist_id));

-- itens: membros editam; público lê
drop policy if exists playlist_items_select on public.playlist_items;
create policy playlist_items_select on public.playlist_items
    for select using (
        public.is_playlist_member(playlist_id)
        or (deleted = false and public.is_public_playlist(playlist_id))
    );

drop policy if exists playlist_items_insert on public.playlist_items;
create policy playlist_items_insert on public.playlist_items
    for insert to authenticated with check (
        added_by = auth.uid()
        and public.is_playlist_member(playlist_id)
    );

drop policy if exists playlist_items_update on public.playlist_items;
create policy playlist_items_update on public.playlist_items
    for update to authenticated
    using (public.is_playlist_member(playlist_id))
    with check (public.is_playlist_member(playlist_id));

-- curtidas: só o dono
drop policy if exists likes_select on public.likes;
create policy likes_select on public.likes
    for select to authenticated using (user_id = auth.uid());

drop policy if exists likes_insert on public.likes;
create policy likes_insert on public.likes
    for insert to authenticated with check (user_id = auth.uid());

drop policy if exists likes_update on public.likes;
create policy likes_update on public.likes
    for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- eventos de reprodução: só o dono (todos os dispositivos da mesma conta enxergam)
drop policy if exists play_events_select on public.play_events;
create policy play_events_select on public.play_events
    for select to authenticated using (user_id = auth.uid());

drop policy if exists play_events_insert on public.play_events;
create policy play_events_insert on public.play_events
    for insert to authenticated with check (
        user_id = auth.uid()
        and (
            device_id is null
            or exists (
                select 1 from public.devices d
                where d.id = device_id and d.user_id = auth.uid()
            )
        )
    );

drop policy if exists play_events_update on public.play_events;
create policy play_events_update on public.play_events
    for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- playback state: um snapshot por conta; só o dono
drop policy if exists playback_state_select on public.playback_state;
create policy playback_state_select on public.playback_state
    for select to authenticated using (user_id = auth.uid());

drop policy if exists playback_state_insert on public.playback_state;
create policy playback_state_insert on public.playback_state
    for insert to authenticated with check (
        user_id = auth.uid()
        and (device_id is null or exists (
            select 1 from public.devices d
            where d.id = device_id and d.user_id = auth.uid()
        ))
    );

drop policy if exists playback_state_update on public.playback_state;
create policy playback_state_update on public.playback_state
    for update to authenticated
    using (user_id = auth.uid())
    with check (
        user_id = auth.uid()
        and (device_id is null or exists (
            select 1 from public.devices d
            where d.id = device_id and d.user_id = auth.uid()
        ))
    );

drop policy if exists playback_state_delete on public.playback_state;
create policy playback_state_delete on public.playback_state
    for delete to authenticated using (user_id = auth.uid());

-- follows: grafo visível para autenticados; cada um segue/deixa de seguir
drop policy if exists follows_select on public.follows;
create policy follows_select on public.follows
    for select to authenticated using (deleted = false);

drop policy if exists follows_insert on public.follows;
create policy follows_insert on public.follows
    for insert to authenticated with check (
        follower_id = auth.uid()
        and follower_id <> followed_id
    );

drop policy if exists follows_update on public.follows;
create policy follows_update on public.follows
    for update to authenticated
    using (follower_id = auth.uid())
    with check (follower_id = auth.uid());

-- atividades: próprias ou de quem você segue, se públicas
drop policy if exists activities_select on public.activities;
create policy activities_select on public.activities
    for select to authenticated using (
        actor_id = auth.uid()
        or (deleted = false and is_public = true and public.is_following(actor_id))
    );

drop policy if exists activities_insert on public.activities;
create policy activities_insert on public.activities
    for insert to authenticated with check (actor_id = auth.uid());

drop policy if exists activities_update on public.activities;
create policy activities_update on public.activities
    for update to authenticated
    using (actor_id = auth.uid())
    with check (actor_id = auth.uid());

-- ----------------------------------------------------------------------------
-- 8. Grants
-- ----------------------------------------------------------------------------

grant usage on schema public to anon, authenticated;
grant select on all tables in schema public to anon;
grant select, insert, update, delete on all tables in schema public to authenticated;
grant execute on all functions in schema public to anon, authenticated;

-- ----------------------------------------------------------------------------
-- 9. Storage de capas e avatares
-- ----------------------------------------------------------------------------

insert into storage.buckets (id, name, public)
values ('covers', 'covers', true)
on conflict (id) do update set public = true;

insert into storage.buckets (id, name, public)
values ('avatars', 'avatars', true)
on conflict (id) do update set public = true;

drop policy if exists covers_read on storage.objects;
create policy covers_read on storage.objects
    for select using (bucket_id = 'covers');

drop policy if exists covers_insert on storage.objects;
create policy covers_insert on storage.objects
    for insert to authenticated with check (
        bucket_id = 'covers'
        and (storage.foldername(name))[1] = auth.uid()::text
    );

drop policy if exists covers_update on storage.objects;
create policy covers_update on storage.objects
    for update to authenticated
    using (bucket_id = 'covers' and (storage.foldername(name))[1] = auth.uid()::text)
    with check (bucket_id = 'covers' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists covers_delete on storage.objects;
create policy covers_delete on storage.objects
    for delete to authenticated
    using (bucket_id = 'covers' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists avatars_read on storage.objects;
create policy avatars_read on storage.objects
    for select using (bucket_id = 'avatars');

drop policy if exists avatars_insert on storage.objects;
create policy avatars_insert on storage.objects
    for insert to authenticated with check (
        bucket_id = 'avatars'
        and (storage.foldername(name))[1] = auth.uid()::text
    );

drop policy if exists avatars_update on storage.objects;
create policy avatars_update on storage.objects
    for update to authenticated
    using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text)
    with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists avatars_delete on storage.objects;
create policy avatars_delete on storage.objects
    for delete to authenticated
    using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

-- ----------------------------------------------------------------------------
-- 10. Realtime (playlists, membros, itens, likes, eventos, social)
-- ----------------------------------------------------------------------------

do $$
declare
    t text;
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        foreach t in array array[
            'profiles', 'tracks', 'playlists', 'playlist_members', 'playlist_items',
            'likes', 'play_events', 'playback_state', 'follows', 'activities'
        ] loop
            if not exists (
                select 1 from pg_publication_tables
                where pubname = 'supabase_realtime'
                  and schemaname = 'public'
                  and tablename = t
            ) then
                execute format('alter publication supabase_realtime add table public.%I', t);
            end if;
        end loop;
    end if;
end;
$$;

-- ----------------------------------------------------------------------------
-- 11. Views de recap (mês atual parcial + meses anteriores)
-- ----------------------------------------------------------------------------

create or replace view public.monthly_listening
with (security_invoker = true) as
select
    user_id,
    date_trunc('month', started_at) as month,
    sum(played_ms)::bigint as played_ms,
    count(*)::bigint as plays,
    count(distinct track_id)::bigint as unique_tracks,
    count(*) filter (where completed)::bigint as completed_plays,
    count(*) filter (where skipped)::bigint as skipped_plays
from public.play_events
where deleted = false
group by user_id, date_trunc('month', started_at);

create or replace view public.monthly_top_tracks
with (security_invoker = true) as
select
    user_id,
    date_trunc('month', started_at) as month,
    track_id,
    count(*)::bigint as plays,
    sum(played_ms)::bigint as played_ms
from public.play_events
where deleted = false
group by user_id, date_trunc('month', started_at), track_id;

create or replace view public.monthly_top_artists
with (security_invoker = true) as
select
    e.user_id,
    date_trunc('month', e.started_at) as month,
    t.artist,
    count(*)::bigint as plays,
    sum(e.played_ms)::bigint as played_ms
from public.play_events e
join public.tracks t on t.id = e.track_id
where e.deleted = false
group by e.user_id, date_trunc('month', e.started_at), t.artist;

grant select on public.monthly_listening to authenticated;
grant select on public.monthly_top_tracks to authenticated;
grant select on public.monthly_top_artists to authenticated;

-- ----------------------------------------------------------------------------
-- 12. Recarrega o cache do PostgREST
-- ----------------------------------------------------------------------------

notify pgrst, 'reload schema';
