-- ============================================================================
-- Openfy — 0003: prévia de playlist compartilhada por código
--
-- OBJETIVO
--   Permitir que quem recebe um código de convite escolha:
--     * apenas ouvir a playlist;
--     * salvar uma cópia local;
--     * colaborar (comportamento antigo, via join_playlist).
--
--   A prévia lê somente campos públicos da playlist e das faixas. Não expõe
--   share_code, e-mails, ids internos de membros nem qualquer outro dado
--   privado. A capacidade é limitada a quem possui o código (12 caracteres
--   aleatórios), exatamente como o convite atual.
--
-- COMPATIBILIDADE
--   Migration ADITIVA: não altera tabelas, policies, triggers nem RPCs
--   existentes. Clientes antigos (0.5.1+) continuam funcionando; apenas os
--   novos passam a usar a RPC.
--
-- ORDEM
--   Rodar depois de 0001_initial.sql (e de 0002, se ela já tiver sido
--   aplicada). Pode ser aplicada isoladamente.
-- ============================================================================

create or replace function public.shared_playlist_preview(p_code text)
returns jsonb
language sql
security definer
set search_path = public
stable
as $$
    select jsonb_build_object(
        'id', p.id,
        'name', p.name,
        'description', p.description,
        'cover_url', p.cover_url,
        'updated_at', p.updated_at,
        'owner', jsonb_build_object(
            'handle', coalesce(pr.handle, ''),
            'display_name', coalesce(pr.display_name, ''),
            'avatar_url', coalesce(pr.avatar_url, '')
        ),
        'tracks', coalesce((
            select jsonb_agg(
                jsonb_build_object(
                    'id', t.id,
                    'title', t.title,
                    'artist', t.artist,
                    'album', t.album,
                    'duration', t.duration,
                    'thumbnail', t.thumbnail
                )
                order by pi.rank, t.id
            )
            from public.playlist_items pi
            join public.tracks t on t.id = pi.track_id
            where pi.playlist_id = p.id
              and pi.deleted = false
        ), '[]'::jsonb)
    )
    from public.playlists p
    left join public.profiles pr on pr.id = p.owner_id
    where p.share_code = trim(p_code)
      and p.deleted = false
    limit 1;
$$;

revoke all on function public.shared_playlist_preview(text) from public;
grant execute on function public.shared_playlist_preview(text) to anon, authenticated;

comment on function public.shared_playlist_preview(text) is
    'Prévia pública (somente leitura) de uma playlist a partir do código de convite.';

-- Faz o PostgREST enxergar a RPC imediatamente.
notify pgrst, 'reload schema';
