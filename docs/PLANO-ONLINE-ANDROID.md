# Openfy Online + Android — mapa técnico

Status: planejamento (não implementado)
Base analisada: Openfy 0.1.1 — JVM 21, Compose Desktop, SQLite/JDBC, InnerTubeX desktop, mpv (dois players), Ktor/OkHttp.
Data: 2026-10-26.

---

## 0. Decisões fechadas (2026-10-26)

Estas decisões valem para o v1 e simplificam o escopo:

1. **Conta opcional.** O app continua funcionando 100% local sem login. O login habilita sync, colaboração, social e recap na nuvem. Ao entrar, os dados locais podem ser importados/mergeados.
2. **Playlist colaborativa: todos os membros são editores.** Qualquer membro pode adicionar, remover e reordenar faixas, renomear e trocar capa. O **owner** continua sendo o único que gerencia membros, revoga convite, transfere propriedade e exclui a playlist.
3. **Sem faixas duplicadas na mesma playlist.** A chave lógica é `(playlist_id, track_id)`; adicionar de novo “ressuscita” o item removido em vez de criar outro. Isso simplifica sync e é o comportamento atual do banco.
4. **Handoff simples, sem Connect/Jam/controle remoto ao vivo.** PC e celular não conversam em tempo real. O servidor guarda o último snapshot de reprodução da conta (fila/faixa/posição) para oferecer “continuar de onde parou” no outro aparelho; cada aparelho toca de forma independente.
5. **Android por APK sideload.** Sem Play Store/AAB no v1. Assinatura própria, distribuição por GitHub/Obtainium/arquivo, atualização manual.
6. **Recap mensal contínuo.** O mês atual mostra os números parciais (minutos, top músicas/artistas, etc.) e é atualizado conforme os eventos chegam; meses anteriores ficam consolidados. A agregação pode ser local (eventos sincronizados) para funcionar offline, com o servidor como consolidador opcional.
7. **Social entra no v1 em escopo mínimo:** perfil público, seguir/deixar de seguir, playlists públicas e feed de atividades. “Amigos ouvindo agora” é opt-in e pode entrar depois, para não atrasar o sync.
8. **Backend:** Supabase cloud (Auth + Postgres + RLS + Realtime + Storage). OAuth Spotify/Google continua só para importar playlists, nunca como catálogo.
9. **Android:** `minSdk 26`, `targetSdk 36`, Compose + Media3/ExoPlayer, foreground service para reprodução, APK universal assinado.
10. **Dados por dispositivo:** áudio baixado não é sincronizado (só metadados). Cada aparelho baixa o que quiser; curtidas, playlists, capas, histórico, configurações compatíveis e recap sincronizam.

---

## 1. Resumo executivo

O Openfy atual é **local-first de um único dispositivo**. O banco já tem `updated_at` e tombstones (`deleted`) em curtidas, playlists e itens, o que ajuda, mas ainda faltam as peças que tornam o sync confiável:

1. **Identidade** (conta, dispositivo, sessão).
2. **Backend** (Postgres + RLS + Realtime + Storage).
3. **Protocolo de sincronização** (outbox, cursor, revisão de servidor, resolução de conflito).
4. **Modelo de dados colaborativo** (membros, papéis, convite, ordenação estável, itens duplicados).
5. **Eventos de reprodução ricos** (tempo ouvido, skip, contexto) para histórico/recap.
6. **App Android** com camada compartilhada, Media3/ExoPlayer e as regras de background do Android.

A ordem recomendada é: **backend + sync no desktop primeiro**, depois **playlists colaborativas**, depois **Android**, e por último **handoff/controle remoto e social**. O recap depende de eventos de reprodução bem instrumentados nos dois apps.

Decisão de arquitetura recomendada:

- **KMP (Kotlin Multiplatform)** para domínio, modelos, DTOs, cliente HTTP, auth e motor de sync.
- **Banco por plataforma** no curto prazo (JDBC/SQLite no desktop, Room/SQLite no Android) atrás de interfaces comuns. SQLDelight compartilhado é o ideal a médio prazo, mas aumenta bastante o custo inicial.
- **Supabase** como backend (Auth, Postgres, RLS, Realtime, Storage, Edge Functions).
- **Compose Desktop** continua no PC; **Compose Android + Media3** no celular. Compartilhar tema, tokens e componentes simples; não tentar compartilhar 100% da UI de imediato.

---

## 2. O que o projeto tem hoje

### Pontos fortes para o online

- Kotlin/JVM e Compose — boa base para KMP e para um app Android nativo.
- InnerTubeX é KMP e suporta Android/JVM/iOS; o mesmo motor de busca/extração pode ser usado no celular (com adaptações de token/PO token).
- Schema v3 já tem `updated_at`, `deleted` e `playlist.cover`.
- Curtidas e playlists usam soft delete, o que preserva tombstones para sync.
- Sessão de reprodução já é serializada (fila, índice, posição, volume, shuffle, repeat).
- Backup JSON e arquivo `.openfyplaylist` já existem.
- Crossfade real com dois players e modo DJ blend já validado no desktop.

### Lacunas que impedem sync confiável

| Hoje | Problema para online | Mudança necessária |
|---|---|---|
| Sem `user_id` / `device_id` | não se sabe de quem/qual aparelho veio a mudança | adicionar identidade em todas as entidades sincronizáveis |
| `updated_at` gerado no cliente | relógios diferentes geram conflito errado | usar `server_rev` (sequência do servidor) como verdade |
| Sem outbox/fila de operações | mudança offline não tem como ser reenviada | tabela local `sync_outbox` |
| Sem cursor de sync | não há pull incremental | `sync_cursor` por conta/dispositivo |
| `playlist_item` PK `(playlist_id, id)` | impede a mesma faixa duas vezes e reordenar de forma colaborativa | `item_id` UUID + `rank` fracionário |
| `position` inteiro | reordenar em dois aparelhos gera renumerar tudo | `rank` (LexoRank/fractional indexing) |
| `playlist.cover` é caminho local | não sincroniza entre PC e celular | `cover_url` remoto + cache local |
| `play_event(id, at)` | não mede tempo ouvido nem skip | evento rico com `played_ms`, `completed`, `source`, `device_id` |
| `session.state` é local | não permite handoff PC ↔ celular | `playback_state` no servidor |
| DB JDBC único | não roda no Android | repositório por plataforma ou SQLDelight |
| `AppController`/`App.kt` grandes e desktop-centric | UI e chamadas de plataforma misturadas | extrair core compartilhado e deixar UI por plataforma |
| `StreamServer` local | desnecessário no Android | ExoPlayer consome URL/headers direto (ou proxy local só se necessário) |
| mpv + JNA + bandeja + atalhos | não existem no Android | Media3/ExoPlayer + MediaSessionService |

---

## 3. Três níveis de “online”

É importante separar o que o usuário quer dizer com “atualizar no PC”:

### Nível 1 — Biblioteca sincronizada
Playlists, curtidas, capas, configurações e histórico sobem para a conta. O outro aparelho vê as mudanças ao abrir/voltar ao app. **Não exige tempo real**; resolve com pull + push + realtime opcional.

### Nível 2 — Handoff (“continuar no outro aparelho”)
O servidor guarda um snapshot da reprodução (fila, faixa, posição, shuffle, repeat, dispositivo ativo). O PC mostra “Continuar no celular” ou “Tocar no PC”; o outro aparelho retoma da posição. **Exige snapshot + presence**, mas não controle remoto contínuo.

### Nível 3 — Controle remoto / Jam
Um aparelho é o “player ativo”; os outros mandam comandos (play/pause/seek/next/add) por WebSocket/Realtime. É o “Spotify Connect” e o “Jam”. **É o mais caro**: presença, heartbeat, autorização, latência, reconexão, fila colaborativa e resolução de conflitos de reprodução.

Recomendação: entregar **Nível 1 + 2**, e só depois avaliar **Nível 3**.

---

## 4. Arquitetura alvo

```
┌─────────────────────┐        ┌─────────────────────┐
│  Openfy Desktop     │        │  Openfy Android     │
│  Compose Desktop    │        │  Compose + Media3   │
│  mpv A/B + EQ       │        │  ExoPlayer A/B      │
└──────────┬──────────┘        └──────────┬──────────┘
           │                              │
           └──────────────┬───────────────┘
                          │
                 ┌────────▼────────┐
                 │  shared (KMP)   │
                 │  models/DTOs    │
                 │  auth/sync      │
                 │  repositories   │
                 │  stats/recap    │
                 └────────┬────────┘
                          │ HTTPS / Realtime
                 ┌────────▼────────┐
                 │    Supabase     │
                 │ Auth            │
                 │ Postgres + RLS  │
                 │ Realtime        │
                 │ Storage (capas) │
                 │ Edge Functions  │
                 └─────────────────┘
```

### Módulos Gradle sugeridos

```
:shared          KMP — domínio, DTOs, sync, auth, API, regras de playlist
:desktopApp      JVM — Compose Desktop, mpv, tray, atalhos, arquivos locais
:androidApp      Android — Compose, Media3, WorkManager, MediaSession
```

No curto prazo, `:shared` pode conter apenas o contrato de sync e DTOs; o banco continua JDBC no desktop e Room no Android. A unificação com SQLDelight pode vir depois, quando o contrato estiver estável.

### Por que não migrar tudo para Compose Multiplatform de uma vez

- O `App.kt` atual tem mais de 3.000 linhas e usa `Window`, `Tray`, `JFileChooser`, `TooltipArea`, atalhos e JNA.
- Android precisa de navegação inferior, gestos, notificação, foreground service, SAF e permissões — layouts diferentes.
- Compartilhar o **design system** (cores, tipografia, cards, TrackRow, botões) já captura a maior parte do ganho visual sem bloquear o Android em um refactor gigante.

---

## 5. Backend recomendado: Supabase

### Por que

- Postgres relacional (playlists/itens/membros/eventos encaixam bem).
- Auth pronta (e-mail/senha, verificação, reset, OAuth opcional).
- RLS (Row Level Security) para autorização por playlist/membro.
- Realtime para mudanças de playlist e presença.
- Storage para capas/avatares.
- Edge Functions para recap, convites e tarefas periódicas.
- Plano gratuito serve para desenvolvimento/poucos usuários; Pro ~US$ 25/mês quando passar dos limites.
- Pode ser self-hosted se privacidade/custo exigirem.

### Alternativas

- **Ktor + Postgres + Redis**: controle total, mas dobra o trabalho de auth/Realtime/Storage.
- **Firebase**: bom para push, mas Firestore é menos natural para playlists ordenadas e conflitos; sem SQL/RLS.
- **PocketBase**: simples para MVP, mas menos robusto para sync colaborativo e autorização fina.

Recomendação: **Supabase**.

### Segurança

- RLS em todas as tabelas; nunca usar `service_role` no cliente.
- Funções RPC para mutações sensíveis (entrar em playlist, reordenar, remover membro).
- Código de convite com hash/expiração e rate limit.
- Tokens no cliente: DPAPI/Credential Manager no Windows; Keystore/EncryptedSharedPreferences no Android.
- E-mail verificado, reset de senha e opção de 2FA.
- Política de privacidade e exclusão de conta/dados.

---

## 6. Modelo de dados online

### Tabelas principais

```sql
-- identidade
profiles (
  id uuid primary key references auth.users,
  handle text unique,
  display_name text,
  avatar_url text,
  created_at timestamptz
)

devices (
  id uuid primary key,
  user_id uuid references profiles,
  name text,
  platform text,          -- windows | android
  app_version text,
  last_seen_at timestamptz,
  push_token text,
  created_at timestamptz
)

-- catálogo (cache compartilhado; metadados, sem áudio)
tracks (
  id text primary key,    -- videoId
  title text, artist text, album text,
  duration int, thumbnail text,
  updated_at timestamptz
)

-- playlists
playlists (
  id uuid primary key,
  owner_id uuid references profiles,
  name text not null,
  description text,
  cover_url text,
  visibility text not null default 'private', -- private | unlisted | public
  share_code text unique,
  created_at timestamptz,
  updated_at timestamptz,
  deleted boolean not null default false,
  server_rev bigint not null
)

playlist_members (
  playlist_id uuid references playlists,
  user_id uuid references profiles,
  role text not null default 'editor', -- owner | editor | viewer
  joined_at timestamptz,
  primary key (playlist_id, user_id)
)

playlist_items (
  id uuid primary key,              -- gerado no cliente, permite offline
  playlist_id uuid references playlists,
  track_id text references tracks,
  rank text not null,               -- ordenação fracionária
  added_by uuid references profiles,
  created_at timestamptz,
  updated_at timestamptz,
  deleted boolean not null default false,
  server_rev bigint not null
)

-- curtidas
likes (
  user_id uuid references profiles,
  track_id text references tracks,
  created_at timestamptz,
  updated_at timestamptz,
  deleted boolean not null default false,
  server_rev bigint not null,
  primary key (user_id, track_id)
)

-- reprodução/recap
play_events (
  id uuid primary key,
  user_id uuid references profiles,
  device_id uuid references devices,
  track_id text references tracks,
  started_at timestamptz not null,
  played_ms int not null default 0,
  duration_ms int,
  completed boolean not null default false,
  skipped boolean not null default false,
  source text,              -- search | playlist | album | radio | mix | downloads
  context_id text,          -- id da playlist/álbum/rádio
  created_at timestamptz not null default now()
)

-- handoff / connect
playback_state (
  user_id uuid references profiles,
  device_id uuid references devices,
  track_id text,
  queue jsonb not null default '[]',
  index int not null default -1,
  position_ms int not null default 0,
  playing boolean not null default false,
  shuffle boolean not null default false,
  repeat text not null default 'OFF',
  updated_at timestamptz not null,
  active boolean not null default false,
  primary key (user_id, device_id)
)

-- social/atividade (fases posteriores)
follows (
  follower_id uuid references profiles,
  followed_id uuid references profiles,
  created_at timestamptz,
  primary key (follower_id, followed_id)
)

activities (
  id bigserial primary key,
  user_id uuid references profiles,       -- dono do feed
  actor_id uuid references profiles,
  playlist_id uuid references playlists,
  type text not null,                     -- item_added | playlist_renamed | member_joined | like
  payload jsonb not null default '{}',
  created_at timestamptz not null default now()
)
```

### Decisões de modelagem

1. **Item de playlist com `id` próprio** em vez de PK composta. Isso permite:
   - a mesma faixa repetida;
   - reordenar sem renumerar;
   - sync de “mover item X” sem ambiguidade;
   - tombstones por item.
2. **Rank fracionário** (`rank text`, ex.: `a0`, `a0V`, `a1`) em vez de `position int`:
   - inserir entre dois itens sem tocar no resto;
   - dois aparelhos podem inserir em posições diferentes sem renumerar tudo;
   - o servidor normaliza/rebalanceia quando necessário.
3. **`server_rev`** por linha, atribuído pelo servidor (sequence/trigger):
   - pull incremental confiável;
   - não depende do relógio do celular/PC;
   - tombstones continuam visíveis até todos sincronizarem.
4. **Capa remota + cache local**:
   - `cover_url` no servidor/Storage;
   - `cover_local`/cache por dispositivo;
   - migrar caminhos antigos de `playlist.cover`.
5. **`playlist_members` com papéis**:
   - `owner` (renomeia/exclui/membros);
   - `editor` (adiciona/remove/reordena);
   - `viewer` (só lê).
6. **Visibilidade**:
   - `private`: só membros;
   - `unlisted`: link/código;
   - `public`: aparece em perfis/busca (fase social).

### RLS (resumo)

- `playlists`: leitura se owner, membro, ou `public`; escrita via RPC/owner/editor conforme campo.
- `playlist_items`: leitura/escrita se membro com papel adequado.
- `likes`, `play_events`, `playback_state`: somente o próprio `user_id`.
- `tracks`: leitura para autenticados; escrita via Edge Function/importador.
- `playlist_members`: leitura para membros; alteração só owner.
- `share_code`: resolução via RPC `join_playlist(code)`, não por SELECT direto.

---

## 7. Protocolo de sincronização

### 7.1 Outbox local

Toda mutação que precisa ir para a nuvem grava uma operação local **na mesma transação** que altera o banco:

```sql
create table sync_outbox (
  op_id text primary key,        -- UUID do cliente
  entity text not null,          -- playlist | playlist_item | like | play_event | playback_state
  op text not null,              -- create | update | delete | move | like | unlike
  payload text not null,         -- JSON
  base_rev integer not null default 0,
  attempts integer not null default 0,
  created_at integer not null
);

create table sync_state (
  key text primary key,
  value text not null
); -- sync_cursor, last_pull_at, last_push_at, account_id
```

Fluxo:

1. UI altera o banco local e insere a operação no outbox (otimista).
2. Worker de sync pega um lote (ex.: 50 ops) e envia via RPC.
3. Servidor valida permissão, aplica em transação, incrementa `server_rev` e devolve as linhas canônicas.
4. Cliente aplica as linhas devolvidas, remove as ops aplicadas e atualiza o cursor.
5. Em conflito, o servidor devolve a versão canônica e o cliente reaplica o que ainda fizer sentido (ou mostra aviso).

### 7.2 Pull incremental

- Cursor por conta: `sync_cursor = último server_rev aplicado`.
- `GET/RPC pull_changes(since, limit=500)` retorna linhas com `server_rev > since`, incluindo tombstones e mudanças de playlists das quais o usuário é membro.
- Aplicar em ordem de `server_rev`; atualizar cursor.
- No primeiro login: snapshot completo + cursor inicial; importar dados locais como um “lote de migração”.

### 7.3 Realtime

- Canal por usuário (`user:{id}`) para biblioteca e playback.
- Canal por playlist (`playlist:{id}`) para membros.
- Ao receber evento, comparar `server_rev` com o local; se maior, aplicar/pull.
- Presence para “quem está online” e “tocando agora” (opcional/privacidade).
- Fallback: polling a cada 30–60 s quando Realtime cair.

### 7.4 Resolução de conflitos

| Entidade | Política |
|---|---|
| Nome/capa/descrição de playlist | LWW pelo `server_rev` (última operação aplicada vence) |
| Curtida | idempotente (`set_like` com liked boolean); LWW |
| Item de playlist (add/remove) | operação atômica por `item_id`; tombstone vence remoção |
| Reordenação | operação `move(item_id, before/after)` + rank fracionário; servidor normaliza |
| Playback state | por dispositivo, sem conflito direto; campo `active` define quem manda |
| Configurações | LWW por chave, com opção de “não sincronizar” (ex.: volume local) |

**Importante:** não usar `updated_at` do cliente como árbitro. Ele serve para exibir “atualizado há X”, não para decidir quem vence.

### 7.5 Migração dos dados locais existentes

- No primeiro login, oferecer “Importar biblioteca deste computador”.
- Enviar playlists, itens, curtidas e eventos como um lote marcado `migration=true`.
- Deduplicar:
  - playlists por nome + owner + período? Melhor pedir confirmação;
  - itens por `track_id` + playlist (se duplicatas não forem desejadas);
  - curtidas por `user_id + track_id`.
- Guardar IDs locais para não duplicar se o usuário repetir a importação.
- Fazer backup automático antes da migração.

---

## 8. Playlists colaborativas

### Fluxo do produto

1. Dono abre a playlist → **Compartilhar** → gera link/código com permissão (`viewer` ou `editor`) e validade.
2. Outra pessoa:
   - abre o link no PC (deep link `openfy://join/CODE`) ou no Android (intent filter);
   - ou cola o código em “Entrar em playlist”.
3. Servidor valida o código, adiciona em `playlist_members` e devolve a playlist.
4. Todos os membros recebem mudanças em tempo real.
5. Dono pode remover membros, mudar papel, revogar link e transferir propriedade.

### O que precisa existir

- Conta e login nos dois aparelhos.
- Backend com RLS + RPC de join.
- `playlist_members` com papéis.
- Realtime por playlist.
- Presença (avatares de quem está online) — opcional no MVP.
- Atividade (“Fulano adicionou X”) — opcional, mas dá o “Spotify vibe”.
- Storage para capa (se o dono trocar, todos veem).
- Modal de membros/permissões.
- Deep links no desktop e Android.
- Rate limit e moderação para links públicos.

### O que **não** precisa no MVP

- Edição simultânea de texto em tempo real (tipo Google Docs) — playlist é uma lista, não texto.
- CRDT completo. Rank fracionário + operações atômicas resolve o caso de uso.
- WebSocket próprio; Realtime do Supabase basta.

### Itens duplicados

Decidir agora:

- **Permitir duplicatas** (mais Spotify-like): `item_id` próprio; “adicionar” sempre cria novo item.
- **Não permitir** (mais simples): validar `(playlist_id, track_id)` e ressuscitar o item existente.

Recomendação: permitir duplicatas, porque o usuário espera isso de playlist e o modelo com `item_id` já resolve.

---

## 9. Outras funções “Spotify vibes”

| Função | Precisa de | Complexidade | Observação |
|---|---|---|---|
| Playlist colaborativa | auth + members + realtime + rank | M | base de tudo |
| Link público/privado de playlist | share_code + RLS + deep link | S–M | depende de members |
| Perfil público | profiles + visibilidade | S–M | decidir se expõe histórico |
| Seguir usuários/artistas | follows + feed | M | artistas já existem no catálogo |
| Feed de atividade | activities + realtime/pull | M | “amigo curtiu/adicionou” |
| Amigos ouvindo agora | presence + privacidade | M | opt-in obrigatório |
| Handoff PC ↔ celular | playback_state + devices | M | recomendado antes do Connect |
| Controle remoto (Connect) | WebSocket/presence + comandos | L | player ativo + heartbeat |
| Jam / listening party | host + fila colaborativa + votos | L | pode reutilizar members/queue |
| Home personalizada / mixes | play_events + likes + agregação | M–L | começar com regras simples |
| Recap mensal/anual | play_events ricos + agregação | M | depende de instrumentação |
| Álbuns/artistas salvos | nova tabela `saved` | S–M | expandir biblioteca |
| Fila sincronizada | playback_state | M | mesmo snapshot do handoff |
| Notificações de novidades | follows + FCM + jobs | M–L | Android push |
| Download offline por aparelho | WorkManager + storage | M | áudio não sincroniza |
| Letras/credits | já existe | — | Android reutiliza LRCLIB |
| Rádio/autoplay | já existe | — | Android reutiliza InnerTubeX |
| Importar Spotify/YTM | já existe | — | manter OAuth só para importar |
| Busca pública de playlists | visibility + índice | M | fase social |

### Recomendação de prioridade

1. Sync de biblioteca (curtidas, playlists, itens, capas).
2. Playlist colaborativa + link.
3. Handoff “continuar no outro aparelho”.
4. Eventos/recap.
5. Perfil/follow/feed.
6. Connect/Jam.
7. Home personalizada/notificações.

---

## 10. Android: o que muda

### 10.1 Estrutura e build

- Criar `:androidApp` com Android Gradle Plugin, `compileSdk 36`, `minSdk 26`, `targetSdk 36`.
- Reutilizar `innertubex` no target Android (o projeto é KMP; confirmar o artefato Android e a configuração de PO token/WebView).
- `:shared` KMP com Kotlin Serialization, Ktor, coroutines; usar `expect/actual` para relógio, armazenamento seguro e conectividade.
- Banco:
  - **Curto prazo:** Room no Android, JDBC no desktop, mesmas tabelas/contrato.
  - **Médio prazo:** SQLDelight KMP com schema único; migrar o desktop.
- UI: Compose Android, Material3, bottom navigation, edge-to-edge, mini player.
- Ícone/logo: adaptar o PNG/ICO atual para mipmap/adaptive icon.

### 10.2 Reprodução

| Desktop | Android |
|---|---|
| mpv A/B | Media3 ExoPlayer A/B (ou um player com crossfade customizado) |
| `set_property volume` | `player.volume` + rampa equal-power |
| FFmpeg equalizer/highpass | `android.media.audiofx.Equalizer` / `DynamicsProcessing` |
| StreamServer local | URL + headers direto no ExoPlayer; proxy local só se necessário |
| Tray/media keys | MediaSessionService + notificação + Bluetooth |
| JNA/Windows | AudioManager/audio focus |
| Download com `java.net.http` | WorkManager + OkHttp + armazenamento do app |
| Sessão em SQLite | Room/SQLDelight + DataStore |

Cuidados:

- **Crossfade no Android**: dois ExoPlayers simultâneos exigem áudio foco e sessão bem gerenciados; o segundo player deve ser pré-carregado e mudo. Validar em Bluetooth, fones e alto-falante.
- **EQ**: as bandas do `Equalizer` variam por aparelho (normalmente 5 bandas, mas frequências diferentes). Mapear 60/230/910/3600/14000 Hz para as bandas disponíveis; se não houver, usar `DynamicsProcessing` (API 28+) ou desabilitar.
- **DJ blend**: highpass de 120 Hz no Android não é tão cirúrgico; usar `DynamicsProcessing`/EQ e aceitar aproximação.
- **Background**: Android 14+ exige `foregroundServiceType="mediaPlayback"`, permissão `FOREGROUND_SERVICE_MEDIA_PLAYBACK` e notificação; Android 13+ pede `POST_NOTIFICATIONS`. Sem isso, a música para ao minimizar.
- **Android Auto / lockscreen**: MediaSession + MediaBrowserService; pode ficar para fase 2.
- **Bateria**: evitar polling agressivo; WorkManager para sync e downloads; Realtime só em foreground ou com conexão controlada.

### 10.3 Extração e rede

- InnerTubeX no Android já lida com cipher/PO token, mas validar:
  - WebView/Consent para PO token em dispositivos sem Play Services;
  - armazenamento do config/remote player;
  - headers de stream no ExoPlayer (User-Agent, Range, Cookie quando necessário).
- Ktor com engine Android/OkHttp.
- Conectividade: `ConnectivityManager`; modo offline explícito.
- Cache de imagens: Coil (ou Ktor + cache próprio) no lugar do cache Compose Desktop.

### 10.4 UI/UX Android

- Navegação inferior: Início, Buscar, Biblioteca.
- Mini player persistente + tela “Tocando agora” com swipe.
- Fila, letras, timer, EQ, DJ, downloads.
- Compartilhar via share sheet (YouTube Music/Openfy).
- Importar `.openfyplaylist`/CSV via SAF (Storage Access Framework), não `JFileChooser`.
- Deep links:
  - `openfy://join/{code}` para playlist colaborativa;
  - links de playlist/álbum do YTM.
- Login: e-mail/senha, verificação, reset; QR code no PC para parear (opcional).
- Área “Dispositivos”: PC/celular, último acesso, “tocar neste dispositivo”, “desconectar”.
- Indicador de sync: online/offline, pendências, última sincronização.
- Acessibilidade: mesmos cuidados de contraste do desktop; TalkBack; alvos de toque ≥48dp.

### 10.5 Distribuição

- `assembleRelease`/`bundleRelease`; assinatura com keystore; R8/ProGuard.
- APK universal ou por ABI; tamanho estimado 30–50 MB (Compose + Media3 + Ktor + banco).
- **Play Store é risco alto**: o app usa extração não oficial do YouTube e remove anúncios; isso tende a violar políticas do Google/YouTube. Distribuição realista: APK direto/GitHub Releases/Obtainium, ou F-Droid se os requisitos/licenças permitirem.
- Atualizações: Obtainium/GitHub; ou updater próprio.
- FCM funciona em APK sideload com Play Services, mas não é garantido em aparelhos sem Google.
- Privacidade: política de dados, exclusão de conta, consentimento para histórico social.
- Licenças: InnerTubeX, Media3, Compose, Ktor, etc.

---

## 11. Handoff e “tocar no PC”

### MVP (recomendado)

1. Cada dispositivo registra `playback_state` (fila, faixa, posição, modos) a cada 5–10 s e em pause/seek/next.
2. Tela “Dispositivos” mostra o outro aparelho e a faixa atual.
3. Botão **“Continuar no PC”**:
   - celular pausa e envia snapshot final;
   - PC busca o snapshot, valida as faixas (região/disponibilidade), carrega a faixa na posição e pausa ou toca;
   - servidor marca o novo dispositivo como `active`.
4. Não tentar manter os dois tocando em sincronia de milissegundos no MVP — isso é Connect.

### Fase posterior (Connect/Jam)

- Canal Realtime `playback:{user}`.
- Player ativo publica estado; os outros enviam comandos.
- Heartbeat/presença; timeout se o player cair.
- Host do Jam escolhe quem controla; convidados adicionam/votam.
- Conflitos: host é autoridade; comandos idempotentes; versão de estado.

---

## 12. Eventos e recap

### Instrumentação

Hoje `Player.load()` chama `db.recordPlay(track.id)` uma vez. Para recap é preciso:

- `started_at`, `played_ms`, `duration_ms`, `completed`, `skipped`.
- Marcar início quando o áudio realmente começa (não no preload).
- Acumular tempo enquanto `playing`; pausar acumulador em pause.
- Fechar evento em: próxima faixa, stop, seek para outra faixa, fim, saída do app.
- Crossfade: contar o tempo de cada faixa sobreposta (as duas são ouvidas).
- Android: `Player.Listener` (onIsPlayingChanged, onPositionDiscontinuity, onMediaItemTransition).

### Envio

- Inserir evento local imediatamente.
- Enviar em lote (ex.: 20–50) via `push_play_events`.
- Eventos são append-only (não têm conflito); usar `id` UUID do cliente para idempotência.
- Guardar `device_id`, `source` e `context_id` para estatísticas.

### Agregação

- Views/materialized views no Postgres:
  - minutos por dia/mês/ano;
  - top tracks/artistas/álbuns;
  - artistas descobertos no período;
  - sequência de dias ouvindo;
  - divisão por dispositivo (PC/celular);
  - horários de pico.
- Edge Function `recap?year=2026&month=...` devolve JSON.
- Cliente renderiza; fallback local se offline/não logado.
- Compartilhar recap como imagem (Compose Canvas) sem servidor de imagem.

### Privacidade

- Recap e histórico sociais são opt-in.
- Não expor “amigos ouvindo” por padrão.
- Permitir apagar eventos e conta.
- Defasagem: eventos antigos locais (antes da conta) podem ser importados uma vez, marcados como `migrated`.

---

## 13. O que fazer no código atual (ordem de refactor)

### Fase A — preparação do domínio

1. Extrair modelos/DTOs/serialização para `:shared` (Track, Playlist, Item, evento, snapshot).
2. Criar interfaces de repositório (`LibraryRepository`, `PlaylistRepository`, `PlayEventRepository`, `PlaybackStateRepository`).
3. Adaptar `Db.kt` do desktop para implementar as interfaces.
4. Criar `SyncEngine` compartilhado (outbox, pull, push, cursor) com testes headless.
5. Adicionar `server_rev`, `device_id`, `user_id` e outbox ao SQLite local (migração v4).
6. Migrar `playlist_item` para `item_id` + `rank`; migrar dados existentes.
7. Separar `playlist.cover` em `cover_url` + cache local.

### Fase B — backend

1. Criar projeto Supabase; migrations SQL; RLS; buckets.
2. Auth no desktop (login, logout, refresh, sessão segura).
3. RPCs de sync e mutações.
4. Tela de conta/dispositivos/sync em Ajustes.
5. Testes de duas instâncias desktop (dois bancos locais) contra o mesmo backend.

### Fase C — Android

1. Criar `:androidApp` e `:shared`; fazer o build compilar.
2. Login/sync; biblioteca local (Room).
3. Busca/artista/álbum/playlist via InnerTubeX Android.
4. Player Media3 + fila + sessão + background.
5. Downloads/offline + notificações.
6. UI/tema e compartilhamento.
7. Testes em aparelho real (Bluetooth, tela bloqueada, modo economia).

### Fase D — colaboração e cross-device

1. Membros/papéis/convite.
2. Realtime/presence.
3. Handoff.
4. Connect/Jam (avaliar depois).
5. Social/feed/recap.

---

## 14. Estimativa de esforço (ordem de grandeza)

Premissas: um desenvolvedor experiente, código atual como base, sem contar revisão jurídica. Não é prazo contratual.

| Bloco | Esforço | Depende de |
|---|---|---|
| Extrair core KMP + interfaces de repositório | M | nada |
| Schema v4 local + outbox/cursor | M | core |
| Supabase (schema, RLS, auth, RPCs) | M | decisão de backend |
| Sync desktop (push/pull/conflitos) | L | backend + schema |
| Playlists colaborativas + convite + realtime | M | sync |
| Capas no Storage + cache | S–M | backend |
| Eventos ricos + recap | M | sync + player |
| Android fundação (build, auth, sync, Room) | L | core + backend |
| Android reprodução/background/EQ/crossfade | L | fundação |
| Android UI completa | L | fundação |
| Handoff PC ↔ Android | M | apps + sync |
| Connect remoto/Jam | L | handoff |
| Social/perfil/feed/notificações | L | backend + Android |
| Distribuição/assinatura/legal | S–M | apps |

Faixa realista para um **v1 sólido com PC + Android + sync + colaboração + recap**: alguns meses de trabalho, não algumas semanas. O Android sozinho (paridade razoável) é o maior bloco.

---

## 15. Riscos e decisões

### Riscos técnicos

- **YouTube/InnerTube**: mudanças frequentes quebram extração; precisará acompanhar versões da InnerTubeX.
- **PO token/WebView no Android**: pode exigir ajustes por dispositivo.
- **Crossfade no Android**: dois players + audio focus + Bluetooth é sensível; validar cedo.
- **Sync offline**: conflitos de reordenação/duplicatas; testar com dois bancos reais.
- **Realtime**: conexões caem; precisa fallback e reconciliação.
- **Handoff**: faixa indisponível na outra região/plataforma; ter fallback.
- **Capas**: storage, cache, migração de caminhos locais.
- **Bateria/background**: Android restritivo; foreground service bem configurado.

### Riscos de produto/legais

- **Play Store**: alta chance de rejeição por usar extração não oficial do YouTube e remover anúncios. Planejar distribuição por APK/GitHub/Obtainium.
- **Termos do YouTube**: o uso de InnerTube não oficial pode violar ToS; funcionalidades sociais/públicas aumentam exposição.
- **Direitos autorais**: não sincronizar arquivos de áudio; compartilhar apenas IDs/metadados.
- **Privacidade**: histórico de escuta e “amigos ouvindo” exigem consentimento e exclusão.
- **Custos**: Supabase Pro, storage de capas, FCM (grátis), domínio, assinatura de APK.
- **Conta**: e-mail verificado, reset, possibilidade de 2FA; evitar perda de biblioteca.

### Decisões que precisam ser tomadas antes de codar

1. **Conta obrigatória ou opcional?**
   - Recomendado: modo local sem conta; login opcional para sync/colaboração.
2. **Playlists colaborativas**: papéis `viewer/editor` ou todos editores?
3. **Duplicatas**: permitir a mesma faixa repetida?
4. **Visibilidade padrão**: privada ou não listada?
5. **Handoff**: só “continuar de onde parou” ou controle remoto ao vivo?
6. **Android**: APK sideload (recomendado) ou tentar Play Store?
7. **Recap**: mensal, anual, ou ambos? Opt-in?
8. **Social**: perfis/follows/feed entram no v1 ou depois?
9. **Backend**: Supabase cloud, self-hosted ou outro?
10. **Capa compartilhada**: Storage do Supabase ou embutir base64 no `.openfyplaylist`? (recomendado Storage para online; base64 só para arquivo offline).

---

## 16. Próximo passo recomendado

1. Fechar as decisões acima (principalmente conta opcional, duplicatas, sideload e escopo social).
2. Criar um **protótipo de sync só no desktop** com duas instâncias locais e Supabase:
   - login;
   - curtidas + playlists + itens;
   - outbox/cursor;
   - realtime;
   - teste de conflito offline.
3. Só depois iniciar o `:androidApp` com playback Media3.
4. Instrumentar `play_event` no desktop desde já (mesmo antes do backend), porque recap e recomendações dependem disso.
5. Deixar Connect/Jam e social para depois de validar sync + Android.

**Regra de ouro:** o banco local continua sendo a fonte da UI; a nuvem é a fonte da verdade para conflitos e sincronização. O app nunca deve travar esperando a rede.
