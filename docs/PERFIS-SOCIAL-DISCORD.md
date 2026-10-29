# Perfis, social e Discord

Data: 2026-10-28. Base: desktop 0.2.9 e Android 0.4.2.

## Resumo

A ideia é viável e o banco já tem metade do caminho: `profiles`, `follows`,
`activities`, `play_events`, playlists públicas e os buckets `avatars`/`covers`
existem desde a `0001_initial.sql`. O perfil próprio (foto, capa, cor, bio,
faixas fixadas e estatísticas) e o Discord Rich Presence do desktop já foram
implementados nas versões 0.2.9/0.4.2. O que falta é a camada social: perfil de
terceiros, follow com privacidade, block/report, feed e as migrations
`0002`/`0003`.

Plano original (mantido como referência; a ordem foi seguida):

1. **Discord Rich Presence no desktop primeiro** (2–4 dias, sem migration).
   É o ganho mais visível por menos risco.
2. **Perfil próprio** (foto, capa, cor, bio, faixas fixadas e estatísticas)
   em desktop e Android, com privacidade opt-in.
3. **Perfil de outra pessoa + follow + playlists públicas** só depois de
   block/report e de uma regra clara de descoberta (handle exato, sem diretório).
4. **Feed/"ouvindo agora"** por último: é a parte com maior risco de privacidade
   e moderação.

Não vale abrir um diretório global de usuários nem expor histórico bruto de
reprodução. O perfil deve ser encontrado por `@handle`/link, com estatísticas
agregadas e visibilidade controlada.

## Estado da implementação (0.2.9 / 0.4.2)

- **Desktop**: tela "Perfil" na sidebar, com avatar e capa (upload para os
  buckets `avatars`/`covers`), nome, bio, cor, efeitos, visibilidade, ordem e
  cartões ocultos das estatísticas, top artistas/faixas e até 10 faixas
  fixadas reordenáveis. O snapshot é sanitizado no cliente, salvo no cache
  local e sincronizado com LWW por `updatedAt`.
- **Android**: a mesma tela pelo Início/Ajustes, com seleção de imagens pela
  galeria, cache em `SharedPreferences` e sync com debounce. O snapshot usa a
  linha privada de `activities` (`type = profile_prefs`) enquanto a `0003` não
  é aplicada, então PC e celular leem e gravam o mesmo perfil.
- **Discord**: Rich Presence no desktop via IPC local, desligado por padrão,
  com toggle em Ajustes › Integrações e Application ID configurável. O payload
  mostra título/artista e play/pause, é limitado por throttle e limpa a
  atividade ao pausar/sair. Teste ao vivo depende de criar o Application no
  Discord Developer Portal e subir os assets `openfy`, `play` e `pause`.
- **Pendente**: aplicar `0002_security_hardening.sql` depois da adoção dos
  apps novos; corrigir `profiles_select`/`follows_select`; criar a `0003` com
  colunas de perfil, pins e RPC de estatísticas; block/report; perfil de
  terceiros e feed.
- **Validação**: 38 testes no desktop e 39 no Android. A 0.4.2 foi instalada
  sobre a 0.3.1 em um Galaxy A15; Home, Perfil com estatísticas reais e o
  diálogo da fila funcionaram, mas edição/upload de imagem ainda não foram
  exercitados no aparelho. O Discord não foi testado com um Application ID
  real.

## O que já existe

| Área | Estado atual | Reaproveitamento |
| --- | --- | --- |
| `profiles` | `id`, `handle` único, `display_name`, `avatar_url`, `is_public` | Base do perfil; capa, bio, cor e efeitos ficam no snapshot privado até a `0003` |
| `follows` | Seguir/deixar de seguir, soft delete | Follow e contadores; a policy atual expõe o grafo inteiro |
| `activities` | Tipos livres, `payload` JSONB, `is_public` | Feed futuro; não usar para "tocando agora" sem opt-in |
| `play_events` | Histórico detalhado, só o dono lê | Estatísticas via RPC `SECURITY DEFINER`, nunca SELECT direto |
| `likes` | Curtidas por usuário | Base para faixas fixadas e top faixas |
| `playlists` | `visibility` público/privado, membros e convite | Playlists públicas no perfil |
| Buckets | `avatars` e `covers`, leitura pública, escrita na pasta do usuário | Avatar e capa sem infraestrutura nova |
| Clients | Desktop e Android já têm tela de perfil, upload de avatar/capa e cache local; falta perfil de terceiros | Reaproveitar `ProfileExtras`, `ProfileScreen` e o snapshot privado entre os dois clients |

### Lacunas e riscos antes de expor perfis

- **`profiles_select` está como `using (true)`.** O campo `is_public` existe,
  mas não esconde nada: qualquer pessoa, inclusive `anon`, lê todos os perfis.
  Antes de qualquer perfil público, a policy precisa respeitar
  `id = auth.uid() or is_public or is_following(id)` e incluir block.
- **`follows_select` libera o grafo inteiro para qualquer autenticado.** Para
  contadores, uma RPC basta; para listas de seguidores, é preciso limitar a
  perfis públicos ou ao próprio usuário.
- **`play_events` é só do dono.** Estatísticas de terceiros exigem uma RPC que
  valide a visibilidade e devolva apenas agregados (minutos, top faixas,
  top artistas, dias ativos), nunca os eventos crus.
- **Não existe block/report.** Sem isso, perfil público vira vetor de assédio.
  Block é pré-requisito para abrir a visualização de terceiros.
- ~~**Android ainda não sobe imagem.**~~ **Resolvido na 0.4.2**: avatar e capa
  são escolhidos pela galeria e enviados ao mesmo Storage do desktop.
- ~~**Não há cache offline do perfil.**~~ **Resolvido na 0.4.2**: snapshot
  local por usuário, com LWW e sync posterior quando a rede volta.

## Produto: perfil próprio

- Cabeçalho com capa (upload ou gradiente), avatar, nome, `@handle`, bio curta
  e contadores de seguidores/seguindo.
- **Faixas fixadas**: até 10 músicas escolhidas pelo usuário, com ordem
  arrastável ou setas. Não confundir com "mais ouvidas", que é estatística.
- **Estatísticas**: minutos ouvidos, execuções, top faixas, top artistas,
  dias ativos/streak. O usuário escolhe quais cartões aparecem, em que ordem,
  e a visibilidade (`privado`, `seguidores`, `público`).
- **Aparência**: paleta de acento, capa com blur/gradiente, layout do perfil e
  movimento sutil. Usar presets validados; não aceitar CSS/HTML/cores livres.
- **Editar**: nome, bio, avatar, capa, cor, efeitos, privacidade. Pré-visualizar
  antes de salvar e manter cópia local para abrir offline.

## Produto: perfil de outra pessoa

- Mesma tela, somente leitura: capa, avatar, bio, playlists públicas, faixas
  fixadas e estatísticas permitidas.
- Botão **Seguir/Seguindo**, contadores e "Tocar tudo"/"Aleatório" nas faixas
  fixadas e playlists públicas.
- Menu com **Bloquear** e **Denunciar**. Bloquear esconde perfil, follows,
  playlists e estatísticas nos dois sentidos.
- Descoberta por `@handle` exato, link compartilhado ou QR. Sem busca global
  por nome enquanto não houver moderação.
- Se o perfil for privado, só o próprio usuário e quem ele aprovar veem.
  Follow não deve dar acesso automático ao histórico; apenas ao que a
  visibilidade permitir.

## Modelo de dados proposto (migration 0003)

> Este SQL é um rascunho de projeto. Não aplicar antes de decidir privacidade,
> moderação e o contrato dos clients novos.

```sql
-- Perfil
alter table public.profiles
    add column if not exists bio text not null default '',
    add column if not exists cover_url text not null default '',
    add column if not exists accent text not null default 'openfy',
    add column if not exists style jsonb not null default '{}'::jsonb,
    add column if not exists stats_visibility text not null default 'private',
    add column if not exists stats_prefs jsonb not null default '{}'::jsonb,
    add column if not exists show_activity boolean not null default false;

-- Limites (idempotência via DO/drop constraint)
-- bio <= 280 caracteres
-- stats_visibility in ('private','followers','public')
-- style e stats_prefs <= 2 KB cada

-- Faixas fixadas (ordem explícita)
create table if not exists public.profile_pins (
    user_id uuid not null default auth.uid()
        references public.profiles(id) on delete cascade,
    track_id text not null references public.tracks(id) on delete cascade,
    position int not null default 0 check (position between 0 and 9),
    created_at timestamptz not null default now(),
    primary key (user_id, track_id)
);

-- Bloqueios
create table if not exists public.blocks (
    blocker_id uuid not null default auth.uid()
        references public.profiles(id) on delete cascade,
    blocked_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (blocker_id, blocked_id),
    check (blocker_id <> blocked_id)
);
```

### Privacidade e RLS

- `profiles_select`: `id = auth.uid() or (is_public and not is_blocked(id)) or
  is_following(id)`, somente `authenticated`.
- `is_blocked(p_user)` como `SECURITY DEFINER` para evitar recursão de policy.
- `profile_pins_select`: dono, perfil público ou seguidor; escrita só do dono.
- `blocks_select`: só o blocker; insert/delete só o próprio blocker.
- `follows_select`: limitar a `follower_id = auth.uid() or followed_id =
  auth.uid()` ou a perfis públicos; contadores via RPC.
- `play_events`: continua sem SELECT para terceiros. A RPC
  `profile_stats(p_user)` valida `stats_visibility` e `is_following` e devolve
  apenas agregados.
- `set_profile_pins(text[])`: RPC `SECURITY DEFINER` que valida no máximo 10
  faixas, apaga e reinsere na ordem recebida; falha se a faixa não existir em
  `tracks`.
- Índices: `play_events (user_id, started_at desc) where deleted = false`,
  `likes (user_id) where deleted = false`, `follows (followed_id) where
  deleted = false`.

### Estatísticas

`profile_stats(p_user)` devolve algo como:

```json
{
  "minutes": 1234,
  "plays": 456,
  "likes": 20,
  "playlists": 5,
  "followers": 10,
  "following": 3,
  "top_tracks": [{"id": "...", "title": "...", "artist": "...", "plays": 31}],
  "top_artists": [{"name": "...", "plays": 80}],
  "daily": [{"day": "2026-10-01", "minutes": 30}]
}
```

- Sem `genre`: a tabela `tracks` não guarda gênero; não prometer "top gêneros".
- Streak e "dias ativos" podem ser calculados no client a partir de `daily`.
- Cachear o resultado por alguns minutos; invalidar ao terminar uma faixa ou
  ao mudar a visibilidade.
- Não expor `play_events` cru, nem "quem ouviu o quê".

### Storage

- Avatar: `avatars/{user_id}/avatar-{timestamp}.jpg`, recortado quadrado,
  no máximo 1024×1024, JPEG qualidade ~85, reencodado para remover EXIF.
- Capa: `covers/{user_id}/profile-{timestamp}.jpg`, 1600×600, JPEG ~85.
- Limite de 2 MB por arquivo no cliente e, se possível, no bucket.
- Nome com timestamp para evitar cache antigo; apagar a versão anterior é
  opcional (custo pequeno).
- Buckets são públicos: não subir imagem sensível; a URL é compartilhável.

## Efeitos e personalização

Manter previsível e barato:

- **Cores**: paletas existentes do app (`ColorPalette`) em vez de hex livre.
  Isso preserva contraste e a identidade visual.
- **Capa**: upload, gradiente da paleta ou capa desfocada. No Android, evitar
  blur em tempo real; gerar miniatura desfocada no upload ou usar um overlay
  estático.
- **Movimento**: opção "sutil" (equalizador animado, parallax leve) e respeito
  a "reduzir animações" do sistema.
- **Layout**: grade/lista para playlists e faixas fixadas.
- `style` deve ser um JSON pequeno com chaves conhecidas; o client ignora
  chaves desconhecidas e o banco limita o tamanho. Nada de HTML/CSS.

## Discord Rich Presence

### Desktop (implementado na 0.2.9)

O Rich Presence pode ser definido **sem OAuth** enquanto o Discord desktop
estiver aberto; basta um Application ID público. O transporte é IPC local:

- Windows: `\\?\pipe\discord-ipc-{0..9}`.
- Linux/macOS: `$XDG_RUNTIME_DIR`, `$TMPDIR`, `$TMP`, `$TEMP` ou `/tmp`.
- Handshake (opcode 0) com `{ "v": 1, "client_id": "..." }`; depois frames
  (opcode 1) com `SET_ACTIVITY`.
- Não precisa de token, segredo ou login do usuário.

Comportamento:

- `type: 2` (Listening), `details` = título, `state` = artista.
- `timestamps.start` = agora − posição, para o tempo andar sozinho.
- `assets.large_image` = asset `openfy` (1024×1024) e `small_image` =
  `play`/`pause`.
- Atualizar em troca de faixa, play/pause e parada; não a cada segundo.
  Throttle para evitar rate limit do Discord.
- Se o Discord não estiver aberto, não bloquear a UI: tentar de novo em segundo
  plano na próxima faixa ou após alguns minutos.
- Ao sair/parar, limpar a atividade.

Implementação atual: `DiscordPresence.kt` conecta ao pipe local, envia
`SET_ACTIVITY` com throttle e reconecta em segundo plano. O toggle e o
Application ID ficam em Ajustes › Integrações; sem ID configurado nada é
enviado. Falta apenas criar o Application e os assets no portal do Discord para
o teste ao vivo.

Privacidade:

- Desligado por padrão; toggle em Ajustes › Integrações.
- Mostrar só título/artista; nunca e-mail, handle ou histórico.
- Opção "modo privado" que mostra "Ouvindo música" em vez do título.
- Só desktop; não enviar dados para servidor nenhum.

Riscos e esforço:

- 2–4 dias, incluindo testes de payload e reconexão.
- Teste real exige Discord aberto; CI cobre só o payload e a máquina de estados.
- Não vale adicionar uma biblioteca pesada; um cliente IPC pequeno com
  `RandomAccessFile` (Windows) e `UnixDomainSocket` (Linux/macOS) resolve.
- Se preferir velocidade, `DiscordIPC` (Java) é uma alternativa, mas adiciona
  dependência e precisa ser avaliada.

### Android

Desde o Social SDK 1.10 (agosto/2026), o Discord suporta Rich Presence no
Android **sem autenticação**, se o app do Discord estiver instalado e logado.
Porém isso exige o SDK nativo (C++/NDK/JNI), aumenta o APK e não é o mesmo
esforço do desktop. Para a 0.4.x, não vale; a 0.4.2 mantém o compartilhamento
pelo share sheet e deixa o Rich Presence só no PC.

Alternativa no Android: botão "Compartilhar faixa" pelo share sheet do sistema.
"Ouvindo agora" entre seguidores é uma feature social separada, com opt-in e
expiração, não Rich Presence.

## Roadmap sugerido

| Fase | Escopo | Esforço aproximado | Dependência |
| --- | --- | --- | --- |
| 0 | Corrigir `profiles_select`/`follows_select`, criar `is_blocked`, RPC de stats e pins (sem UI) | 2–3 dias | Decisão de privacidade |
| 1 | Perfil próprio: avatar, capa, cor, bio, edição e cache offline (desktop + Android) | 5–8 dias | Fase 0 |
| 2 | Faixas fixadas + cartões de estatísticas com ordem/visibilidade | 4–6 dias | Fase 1 |
| 3 | Perfil de terceiros + follow + playlists públicas + block/report | 5–8 dias | Fase 2 |
| 4 | Discord Rich Presence no desktop | 2–4 dias | Nenhuma; pode ser feito antes |
| 5 | Feed de atividades/"ouvindo agora" + moderação | 7–12 dias | Fase 3 + base de usuários |

Sugestão de ordem: **4 → 1 → 2 → 3 → 5**. O Discord entrega valor visível
imediatamente; o perfil próprio organiza a base; o social só entra quando
block/report existir. As fases 1, 2 e 4 estão implementadas em 0.2.9/0.4.2; a
fase 0 (policies/0002) e as fases 3 e 5 seguem pendentes.

## Critérios de aceite

- Migration aplicada em staging; `anon` não lê perfil privado; seguidor vê
  estatísticas somente quando `stats_visibility = 'followers'`; `play_events`
  cru continua negado para terceiros.
- Avatar/capa: upload, troca, limite de tamanho, reencode sem EXIF e URL
  pública abrindo nos dois clients.
- Pins: reordenar, remover, limite de 10, persistir após restart e sincronizar
  entre desktop e Android.
- Estatísticas: números conferem com `played_ms`/`completed`; visibilidade
  privada esconde até de seguidores.
- Perfil offline: último snapshot abre sem rede e mostra a data.
- Discord: faixa aparece no perfil do Discord, play/pause reflete, parar limpa,
  toggle desligado não conecta, Discord fechado não trava o app.
- Testes novos: RLS/RPC (SQL), payload do Discord, pins e cache de perfil.

## Decisões pendentes

1. Perfil novo nasce público ou privado? Recomendação: **privado por padrão**,
   com opt-in explícito ao abrir o perfil pela primeira vez.
2. Estatísticas nascem privadas? Recomendação: **sim**.
3. Descoberta por handle exato ou diretório? Recomendação: **handle/link**, sem
   diretório, até existir moderação.
4. Quais cartões de estatística entram na v1? Minutos, execuções, top faixas,
   top artistas e dias ativos são os mais baratos.
5. Quem modera denúncias? Sem essa resposta, adiar perfis públicos.
6. Criar o Discord Application (ID + assets) antes de começar a fase 4.
