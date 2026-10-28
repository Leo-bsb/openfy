# Segurança Supabase — exposição de `playlists.share_code`

> Status: a correção está no repositório (migration `0002_security_hardening.sql`
> + clientes desktop/Android). **A proteção só vale depois de aplicar a migration
> no projeto Supabase em produção.** Enquanto ela não for aplicada, qualquer
> autenticado (ou anônimo, via policy de playlist pública) ainda consegue ler
> `share_code` por SELECT direto.

## 1. O risco

- `supabase/migrations/0001_initial.sql` (seção 8) concedia
  `grant select on all tables in schema public` para `anon` e
  `grant select, insert, update, delete` para `authenticated` — ou seja, SELECT
  na tabela inteira `public.playlists`, incluindo a coluna `share_code`.
- A policy `playlists_select` (seção 7) expõe as linhas de playlists públicas
  para qualquer um (`visibility = 'public' and deleted = false`).
- Consequência: `GET /rest/v1/playlists?select=share_code` basta para colher
  códigos de convite em massa. Com o código em mãos, `join_playlist(code)`
  promove o atacante a **editor** da playlist alheia (inserção em
  `playlist_members` via RPC SECURITY DEFINER).

Camadas que existiam e não cobriam o caso:

| Camada | O que fazia | Por que não bastava |
|---|---|---|
| RLS `playlists_select` | filtra **linhas** | playlists públicas são visíveis por design; não filtra **colunas** |
| trigger `protect_playlist_fields` | impede **escrita** em share_code por não-owner | não protege leitura |
| `join_playlist` por código | exige conhecer o código | o código era legível por SELECT |

## 2. A correção (migration 0002)

Arquivo: `supabase/migrations/0002_security_hardening.sql`

1. **SELECT por coluna**: revoga `SELECT` de tabela em `public.playlists` de
   `anon`/`authenticated` e concede `SELECT` apenas nas colunas
   `id, owner_id, name, description, cover_url, visibility, created_at,
   updated_at, deleted, server_rev` — `share_code` fica fora.
2. **RPC `public.playlist_share_code(p_playlist_id uuid)`**: `SECURITY DEFINER`,
   `search_path` fixo em `public`, retorna o código somente se `auth.uid()` for
   dono ou membro ativo (`deleted = false`); caso contrário retorna `null`
   (e exceção `not_authenticated` sem sessão). É a única forma nova de obter
   o código.
3. **Grants da RPC**: `revoke ... from public, anon` e
   `grant execute ... to authenticated` — anônimo não chama.
4. **RLS**: políticas de `playlists` redeclaradas sem mudança (idempotência).
5. `notify pgrst, 'reload schema'` no final, para o PostgREST enxergar a RPC e
   as novas permissões de coluna.

A migration é idempotente: pode ser re-executada sem efeito colateral.

## 3. Compatibilidade dos clientes

- **Desktop** (`src/main/kotlin/com/openfy/data/Supabase.kt`):
  - `shareCode()` agora faz `POST /rest/v1/rpc/playlist_share_code` com
    `{"p_playlist_id": "<uuid>"}` (novo `ShareCodeBody`);
  - `playlists()` não seleciona mais `share_code`
    (`select=id,name,updated_at,deleted,cover_url`);
  - o campo `shareCode` continua em `RemotePlaylist` (nunca mais preenchido);
  - callers (`Account.shareCodeFor`, `AppController`) já tratam exceção/null
    via `runCatching`.
- **Android** (`androidApp/.../data/SupabaseApi.kt` + `SupabaseModels.kt`):
  - mesma RPC, mesmo corpo `ShareCodeBody(p_playlist_id)`;
  - os selects de `playlists` já não levavam `share_code`;
  - `AuthViewModel.requestShareCode` já envolve a chamada em `runCatching`.

**Ordem de deploy**: publicar os apps atualizados **antes** de aplicar a
migration. Os apps novos funcionam contra o schema velho (a RPC ainda não
existe e `shareCode()` retorna `null` — o convite apenas fica indisponível até
a migration entrar). Os apps **velhos** param de funcionar com a migration
nova (SELECT em `share_code` passa a dar erro de permissão), então não deixe
a janela entre publicar os apps e aplicar a migration ser longa demais para
usuários que ainda não atualizaram.

## 4. Como aplicar

Opção A — SQL Editor (dashboard do Supabase, role `postgres`):

1. Abra **SQL Editor** no projeto.
2. Cole o conteúdo de `supabase/migrations/0002_security_hardening.sql`.
3. Execute. O `notify pgrst` ao final já recarrega o cache do PostgREST.

Opção B — CLI (se o projeto tiver `supabase/config.toml` versionado):

```bash
supabase link --project-ref <ref>   # uma vez
supabase db push                    # aplica migrations pendentes
```

(No repositório só existe `supabase/migrations/`; não há `config.toml`. A CLI
funciona apontando para o projeto, mas o caminho recomendado hoje é o SQL
Editor, mesmo.)

## 5. Rollback

Se algo quebrar, rode no SQL Editor o bloco comentado no fim da migration:

```sql
drop function if exists public.playlist_share_code(uuid);
revoke select (id, owner_id, name, description, cover_url, visibility,
    created_at, updated_at, deleted, server_rev) on public.playlists
    from anon, authenticated;
grant select on public.playlists to anon, authenticated;
notify pgrst, 'reload schema';
```

Depois do rollback, os apps novos voltarão a receber erro/null na RPC; reverter
os apps para a build anterior restaura o comportamento antigo. O rollback
reabre a exposição — trate como incidente: rode `rotate_share_code` nas
playlists afetadas (via dashboard/SQL, como postgres) se houver suspeita de
coleta de códigos.

## 6. O que testar depois de aplicar

Use dois contas autenticadas (A = dona, B = estranho, C = membro):

1. `GET /rest/v1/playlists?select=share_code` com token de B → deve falhar com
   erro de privilégio insuficiente (SQLSTATE `42501`; o status HTTP devolvido
   pelo PostgREST varia com a versão — 401/403/400 são possíveis). SELECT de
   colunas permitidas (`id,name`) continua funcionando para playlist pública.
2. `POST /rest/v1/rpc/playlist_share_code {"p_playlist_id": "<playlist de A>"}`
   como A → retorna o código. Como C (membro ativo) → retorna o código. Como B
   → retorna `null`. Sem token (anon) → erro/403 (`not_authenticated` ou permissão).
3. Fluxo de convite desktop/Android: A pede o código, B cola o código →
   `join_playlist` adiciona B como editor; o app de A mostra o mesmo código vindo
   da RPC.
4. Playlist soft-deleted: RPC retorna `null`.
5. Rotação (se exposta no futuro): `rotate_share_code` continua apenas para owner.
6. Sync normal: push/pull de playlists, itens e capas sem regressão (a migration
   não toca em INSERT/UPDATE/DELETE).

## 7. Fora do escopo desta migration

- A 0001 ainda dá `grant execute on all functions` para `anon` (seção 8) e
  `grant select on all tables` para `anon` em **outras** tabelas
  (profiles, tracks etc.) — intentional para o social v1, mas vale auditar
  numa segunda passagem (ex.: `join_playlist` aceita código de playlist
  privada? sim — hoje quem sabe o código entra, mesmo em playlist privada;
  considerar exigir que o código venha de uma playlist pública ou confirmar
  com o dono).
- `select=*` em `playlists` por anon agora devolve só as colunas granteadas;
  clientes que dependerem de `*` precisam ser auditados.
