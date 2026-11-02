# Social — SQL aplicado no Supabase

Data: 2026-10-31, atualizado em 2026-11-02. O cliente já faz seguir, listar
seguidores e ver playlists públicas de quem você segue. As policies de `follows`
**já estão aplicadas** no projeto (veja abaixo), então desfazer o follow funciona
pelo `DELETE` do app.

> **Atualização de 02/11/2026:** as policies `follows_update` e `follows_delete`
> foram criadas e o schema foi recarregado. O `UPDATE` (tombstone) ainda pode
> responder `42501` em alguns casos mesmo com a policy permissiva; por isso o app
> usa `DELETE`, que funciona e é o caminho testado.

> **Convite/colaboração não depende mais deste SQL.** A migration 0002 (RPC
> `playlist_share_code`) não está aplicada no projeto; o app agora lê
> `playlists.share_code` direto quando a RPC responde 404, então convite e
> compartilhamento voltam a funcionar. Aplicar a 0002 é recomendado por
> segurança (hoje qualquer pessoa autenticada que veja uma playlist pública
> consegue ler o `share_code` dela), mas fica adiada enquanto existirem clientes
> antigos (0.5.1) que quebrariam no `SELECT` de `playlists`.

O SQL abaixo é o que está no **SQL Editor** do projeto (role `postgres`); fica
como referência para recriar em outro ambiente:

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
3. **Deixar de seguir** → a linha some (`DELETE`). Se aparecer
   `"Não foi possível deixar de seguir agora; veja docs/SQL-SOCIAL.md."`, confira
   se as policies abaixo ainda existem no projeto.
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
servidor ficam só conta, playlists, `follows` e os snapshots. Os snapshots
sociais são limpos automaticamente por um job do `pg_cron`:

- função: `public.cleanup_activities()`;
- job: `openfy-cleanup-activities`, `17 4 * * *` (04:17 UTC, todo dia);
- comando: `select public.cleanup_activities()`.

A limpeza remove `social_state` com mais de 7 dias, snapshots privados
aposentados (`profile_prefs`/`settings_prefs` com `deleted = true`) com mais de
1 dia e roda `vacuum (full, analyze) public.activities`. O job foi executado
manualmente em 02/11/2026 e está ativo. Para conferir:

```sql
select jobid, schedule, command, active
from cron.job
where jobname = 'openfy-cleanup-activities';

select status, start_time, end_time
from cron.job_run_details
order by start_time desc
limit 10;
```

Se o `pg_cron` não estiver disponível, a mesma limpeza pode ser rodada à mão no
**SQL Editor**:

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
