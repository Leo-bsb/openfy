-- ============================================================================
-- Openfy — hardening de segurança: exposição de playlists.share_code
--
-- PROBLEMA (migration 0001, seção 8):
--   `grant select on all tables in schema public to anon` +
--   `grant select, insert, update, delete ... to authenticated` dão SELECT na
--   tabela inteira, incluindo `share_code`. Somado à policy `playlists_select`
--   (que expõe linhas de playlists públicas para qualquer um), qualquer usuário
--   autenticado — ou anônimo — consegue listar `share_code` de playlist pública
--   e chamar `join_playlist(code)` para virar editor de playlists alheias.
--
-- SOLUÇÃO:
--   1. SELECT de tabela em public.playlists vira SELECT por coluna, sem
--      `share_code` (id, owner_id, name, description, cover_url, visibility,
--      created_at, updated_at, deleted, server_rev).
--   2. Nova RPC `public.playlist_share_code(p_playlist_id uuid)`, SECURITY
--      DEFINER, devolve o código somente para owner ou membro ativo.
--   3. `grant execute` da RPC apenas para `authenticated`.
--
-- ORDEM DE APLICAÇÃO:
--   * Rode esta migration DEPOIS da 0001_initial.sql, como role postgres
--     (SQL Editor ou `supabase db push`).
--   * Publique os clientes atualizados (desktop e Android) ANTES de aplicar.
--     Clientes novos funcionam contra o schema velho (a RPC ainda não existe
--     => shareCode() retorna null; nada quebra). O inverso NÃO vale: assim que
--     esta migration entrar, clientes antigos param de ler `share_code` e o
--     botão "convidar" quebra.
--   * A segurança só vale de fato depois de aplicar esta migration no projeto
--     Supabase em produção. O arquivo no repositório não protege nada sozinho.
--
-- A migration é idempotente (pode ser re-executada).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Privilégios de playlist: SELECT sem share_code
-- ----------------------------------------------------------------------------

-- A revogação abaixo atinge apenas o privilégio SELECT; INSERT/UPDATE/DELETE
-- de authenticated permanecem como na 0001 (o trigger protect_playlist_fields
-- já impede não-owner de alterar share_code/owner/visibility/deleted via UPDATE).
revoke select on public.playlists from anon, authenticated;

grant select (
    id,
    owner_id,
    name,
    description,
    cover_url,
    visibility,
    created_at,
    updated_at,
    deleted,
    server_rev
) on public.playlists to anon, authenticated;

-- ----------------------------------------------------------------------------
-- 2. RPC do código de compartilhamento
-- ----------------------------------------------------------------------------

create or replace function public.playlist_share_code(p_playlist_id uuid)
returns text
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    code text;
begin
    if auth.uid() is null then
        raise exception 'not_authenticated';
    end if;

    -- Dono (mesmo se o trigger de membros falhou) ou membro ativo.
    -- Playlist soft-deleted não devolve código.
    select p.share_code into code
    from public.playlists p
    where p.id = p_playlist_id
      and p.deleted = false
      and (
          p.owner_id = auth.uid()
          or public.is_playlist_member(p.id)
      );

    return code;  -- null quando a playlist não existe ou o chamador não é do círculo
end;
$$;

comment on function public.playlist_share_code(uuid) is
    'Devolve o share_code da playlist apenas para owner ou membros ativos. Substitui o SELECT direto da coluna.';

-- Sem grants: SECURITY DEFINER roda como postgres, mas só autenticados chamam.
revoke all on function public.playlist_share_code(uuid) from public, anon;
grant execute on function public.playlist_share_code(uuid) to authenticated;

-- ----------------------------------------------------------------------------
-- 3. RLS (mantida; redeclarada por clareza e idempotência)
-- ----------------------------------------------------------------------------

alter table public.playlists enable row level security;

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

-- ----------------------------------------------------------------------------
-- 4. Cache do PostgREST (nova RPC + mudança de colunas visíveis)
-- ----------------------------------------------------------------------------

notify pgrst, 'reload schema';

-- ----------------------------------------------------------------------------
-- Rollback (se necessário, rodar manualmente):
--
--   drop function if exists public.playlist_share_code(uuid);
--   revoke select (id, owner_id, name, description, cover_url, visibility,
--       created_at, updated_at, deleted, server_rev) on public.playlists
--       from anon, authenticated;
--   grant select on public.playlists to anon, authenticated;
--   notify pgrst, 'reload schema';
--
--   Depois do rollback, os clientes novos voltam a receber erro ao chamar a
--   RPC; reverter os apps para a build anterior restaura o comportamento viejo.
-- ----------------------------------------------------------------------------
