# Roadmap Spotify para o Openfy

Data: 2026-10-28. Base: desktop 0.2.8 e Android 0.4.0.

## Princípio

O Openfy não deve clonar o Spotify. O maior retorno vem de usar o que já existe
(histórico, curtidas, recomendações, rádio, playlists, downloads e Supabase)
para deixar o produto mais pessoal, rápido e confiável — sem depender de DRM,
API oficial do Spotify ou infraestrutura de tempo real cara.

## P0 — próximo ciclo

1. **Biblioteca de verdade**: álbuns salvos + artistas seguidos.
   - Base: `RemoteCollectionScreen`, `ArtistPage`, cache de tracks, Supabase.
   - Esforço: 4–6 dias + 2–3 de sync.
   - Aceite: salvar/seguir sobrevive ao restart e sincroniza; tela Biblioteca
     com filtros `Playlists | Álbuns | Artistas | Curtidas`.

2. **Home “Feito para você”**: 3 mixes locais + Descobertas da semana.
   - Base: `Recommender`, `startPersonalMix`, rádios do YouTube Music.
   - Esforço: 4–6 dias.
   - Aceite: mixes de 25–50 faixas, sem repetição, cache diário, estado vazio
     quando não há histórico, contexto “Mix: Para você” na fila.

3. **Offline completo**: modo offline, fila de downloads, progresso, retry,
   “baixar curtidas”, “manter playlist offline” e limite de espaço.
   - Base: `Downloader`, `download`, `StreamServer` local, auto-download.
   - Esforço: 4–6 dias.
   - Antes: trocar `ofByteArray` por streaming com `.part`.

4. **Recap compartilhável + Seu ano**: visão anual, top álbuns, streak,
   redescobertas e exportação PNG 1080×1920/1080×1080.
   - Base: `RecapSnapshot`, consultas SQLite, `RecapScreen`, `ArtworkCache`.
   - Esforço: 4–5 dias.

5. **Colaboração v1**: membros/papéis, convite, sair, atualização por polling.
   - Base: `playlist_members`, `share_code`, RPCs Supabase.
   - Esforço: 8–12 dias na fase A; só anunciar depois de corrigir RLS e
     outbox/`server_rev`.

## P1 — depois

- **Smart Shuffle / Enhance**: recomendadas marcadas na fila/playlist, com
  desfazer; base no `Recommender` e snapshots de shuffle.
- **Ferramentas de playlist**: ordenar, buscar, filtrar, multiseleção, pastas.
- **Letras 2.0**: cache offline, offset por faixa, compartilhar trecho, modo
  imersivo.
- **Perfil/avatar e playlists públicas**: buckets `avatars` e schema prontos.
- **SMTC nativo do Windows**: capa/título na central de mídia e Bluetooth.
- **Release Radar local**: novidades de artistas seguidos.
- **Rádio de playlist/álbum e “não tocar esta faixa”**.
- **Fila navegável**: clicar no contexto e ver histórico da sessão.

## P2 — se houver base de usuários

- Handoff “continuar no celular” (`playback_state`/`devices`).
- Connect/Jam entre dispositivos.
- Feed social, follows e “amigos ouvindo agora”.
- Gapless e normalização de volume.
- EQ de 10 bandas/preamp e presets custom.
- Daylist por horário; Last.fm/Discord Rich Presence.
- Tradução de letras com API paga.

## O que não vale a pena agora

- Podcasts/audiolivros, Canvas/clipes, shows/merch, casting DLNA/Chromecast.
- AI DJ por linguagem natural ou BPM/tom (o modo DJ foi removido de propósito).
- OAuth Spotify: o CSV do Exportify resolve o caso principal.
- “Top 1% do mundo” ou comparação global: o Openfy não tem base global.
- Sincronizar arquivos de áudio entre aparelhos: baixe por dispositivo.

## Ganhos rápidos

- Corrigir “horas ouvidas” para usar `played_ms` e “execuções” para `finished=1`.
- Cache de letras no SQLite.
- `device_id` estável no desktop.
- Downloader com streaming e progresso.
- Contexto da fila clicável.
- Cache do recap remoto por 1 hora.

## Riscos

- Sync LWW com push de estado inteiro ainda pode ressuscitar itens; outbox +
  `server_rev` precisam vir antes de colaboração em tempo real.
- Relógio entre dispositivos pode inverter o vencedor do LWW.
- InnerTube/YouTube Music é externo e pode mudar; mixes multiplicam chamadas.
- `Recommender.stats()` lê até 20 mil eventos por chamada; precisa de cache.
- `App.kt`/`AppController.kt` são monólitos; novas telas devem ser extraídas.
- RLS de papéis de playlist precisa exigir owner/editor para escrita.
- Não há testes de UI nem CI.
