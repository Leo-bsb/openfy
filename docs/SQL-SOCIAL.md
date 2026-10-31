# Social — SQL que falta no Supabase

Data: 2026-10-31. O cliente já faz seguir, listar seguidores e ver playlists
públicas de quem você segue. Duas coisas dependem do servidor:

1. **Deixar de seguir** — a policy atual de `follows` recusa `deleted = true`
   (`new row violates row-level security policy`) e não existe policy de
   `DELETE`, então o app não consegue desfazer o follow.
2. (opcional) **Perfil público** — hoje `profiles.is_public` é sempre `true` e a
   bio/cores ficam num snapshot privado (`activities`, `is_public = false`).
   Nada quebra sem isso.

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

## Limites conhecidos

- `playlists_select` só expõe `visibility = 'public'` para quem não é membro;
  playlists privadas de outra conta não aparecem (por design).
- Estatísticas do perfil (`statsVisibility`) ainda não têm leitura pública: o
  snapshot vive em `activities` privado. Para expor, seria preciso uma segunda
  linha pública (`type = profile_public`) — não implementado.
- O feed mostra playlists, não atividades de curtida: `activities` públicas
  exigiriam publicar um evento a cada curtida.
