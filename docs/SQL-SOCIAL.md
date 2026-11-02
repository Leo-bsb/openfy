# Social — SQL que falta no Supabase

Data: 2026-10-31. O cliente já faz seguir, listar seguidores e ver playlists
públicas de quem você segue. Duas coisas dependem do servidor:

1. **Deixar de seguir** — a policy atual de `follows` recusa `deleted = true`
   (`new row violates row-level security policy`) e não existe policy de
   `DELETE`, então o app não consegue desfazer o follow.
2. (opcional) **Perfil público** — hoje `profiles.is_public` é sempre `true` e a
   bio/cores ficam num snapshot privado (`activities`, `is_public = false`).
   Nada quebra sem isso.

> **Convite/colaboração não depende mais deste SQL.** A migration 0002 (RPC
> `playlist_share_code`) não está aplicada no projeto; o app agora lê
> `playlists.share_code` direto quando a RPC responde 404, então convite e
> compartilhamento voltam a funcionar. Aplicar a 0002 é recomendado por
> segurança (hoje qualquer pessoa autenticada que veja uma playlist pública
> consegue ler o `share_code` dela).

Rode no **SQL Editor** do projeto (role `postgres`), depois recarregue o schema:

```sql
-- 1. Desfazer o follow: tombstone (deleted = true) ou delete de verdade.
drop policy if exists follows_update on public.follows;
create policy follows_update on public.follows
    for update to authenticated
    using (follower_id = auth.uid())
    with check (follower_id = auth.uid());

drop policy if exists follows_delete on public.follows;
create policy follows_delete on public.follows
    for delete to authenticated
    using (follower_id = auth.uid());

-- 2. PostgREST recarrega as policies.
notify pgrst, 'reload schema';
```

## Como testar

1. Login com a conta A, Comunidade → Descobrir → busque a conta B → **Seguir**.
2. Confira em `follows` (Table Editor) a linha `follower_id = A`, `followed_id = B`.
3. **Deixar de seguir** → a linha passa a `deleted = true` (ou some, se o app
   usou DELETE). Sem o SQL, o app mostra
   `"Não foi possível deixar de seguir agora; veja docs/SQL-SOCIAL.md."`.
4. Playlists: na playlist própria, ⋮ → **Playlist pública**. Na conta B,
   Comunidade → Seguindo deve listar a playlist e permitir **Ouvir** e
   **Copiar para minha conta**.

## Feed social sem tabela nova

O "tocando agora" e as últimas curtidas viajam numa única linha de
`activities` com `type = 'social_state'` e `is_public = true`. O `payload` tem
`track`, `playing`, `at` e até 20 `likes`. Como `protect_activity_fields`
impede editar o payload, cada publicação **insere** uma linha e marca a
anterior como `deleted = true`; quem lê fica com a mais recente por pessoa
(RLS: só seguidores veem, e só `deleted = false`).

Nenhuma tabela, coluna ou policy nova é necessária: já cabe nas policies
`activities_select` / `activities_insert` / `activities_update` da 0001.

## Banco enxuto (retenção)

O plano gratuito do Supabase tem 500 MB. Tudo que é histórico grande fica no
aparelho (SQLite local, com `VACUUM` e retenção de 400 dias no desktop); no
servidor ficam só conta, playlists, `follows` e os snapshots. Ainda assim os
snapshots se acumulam, então rode esta limpeza de vez em quando no **SQL
Editor** (role `postgres`):

```sql
-- Snapshots sociais antigos: o estado atual tem menos de 1 dia.
delete from public.activities
where type = 'social_state'
  and created_at < now() - interval '7 days';

-- Snapshots privados já aposentados (perfil/ajustes): mantém 1 dia.
delete from public.activities
where type in ('profile_prefs', 'settings_prefs')
  and deleted = true
  and created_at < now() - interval '1 day';

-- Espaço de volta ao disco (roda fora de transação no SQL Editor).
vacuum (full, analyze) public.activities;
```

Ordem de grandeza: uma linha `social_state` tem poucos KB (capa em URL, não em
base64), então 7 dias de uso normal fica na casa de alguns MB. `profiles`,
`playlists` e `follows` são pequenos por natureza; capas e avatares vão para os
buckets `covers`/`avatars` já reduzidos pelo app (720 px avatar, 1280 px capa,
JPEG). Para conferir o peso:

```sql
select relname,
       pg_size_pretty(pg_total_relation_size(c.oid)) as total
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r'
order by pg_total_relation_size(c.oid) desc;
```

## Limites conhecidos

- `playlists_select` só expõe `visibility = 'public'` para quem não é membro;
  playlists privadas de outra conta não aparecem (por design).
- Estatísticas do perfil (`statsVisibility`) ainda não têm leitura pública: o
  snapshot vive em `activities` privado. Para expor, seria preciso uma segunda
  linha pública (`type = profile_public`) — não implementado.
- O feed social é um retrato, não um histórico: cada pessoa aparece uma vez,
  com a faixa atual e as curtidas mais recentes.
