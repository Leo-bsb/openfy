# Avaliação do Openfy Desktop — estado, correções e próximos passos

Data: 2026-10-28. Base avaliada: desktop 0.2.7 e Android 0.3.1.

## Resumo

O desktop está em nível de uso diário para **tocar música local/YouTube Music**.
A base é madura: mpv A/B, servidor de stream próprio, fila editável, sessão
persistida, downloads, letras, EQ, bandeja e atalhos. O que ainda separa o
Openfy de um produto “acabado” não é o player em si, e sim a camada de conta:
sync incremental, recap na nuvem, capas colaborativas e continuidade entre PC e
celular.

Nota prática por área:

| Área | Estado | Nota |
|---|---|---|
| Reprodução/fila | Maduro, dois mpv, crossfade | 9/10 |
| Biblioteca local | SQLite, playlists, curtidas, histórico | 9/10 |
| Busca/exploração | YouTube Music, artista, álbum, rádio | 8,5/10 |
| Conta/sync | Funciona por LWW, sem outbox/server_rev | 6,5/10 |
| Capas | Local + upload para Storage nesta rodada | 8/10 |
| Recap/social | Dados existem; UI e feed ainda limitados | 5/10 |
| Distribuição Windows | EXE/MSI com mpv embutido; sem assinatura | 7,5/10 |
| Testabilidade | Smokes reais fortes; sem testes de UI | 7/10 |

## O que está bem resolvido

- **Streaming correto**: o mpv nunca recebe URL do Google; o `StreamServer`
  reextrai quando a assinatura vence e aceita Range/seek.
- **IPC estável**: duas conexões com o named pipe (leitura e escrita), fila de
  comandos e drenagem de respostas. Isso eliminou o travamento clássico do mpv.
- **Crossfade real**: dois processos mpv, pré-carga muda no standby, rampa
  equal-power.
- **Fila coerente**: shuffle reordena a fila vis?vel e restaura a ordem original ao desligar.
- **Sessão**: fila, faixa, posição, volume, shuffle, repeat, crossfade e
  última tela sobrevivem ao restart.
- **Downloads/offline**: faixa baixada é tocada do disco pelo mesmo servidor
  local, sem depender do YouTube.
- **Custo controlado**: cache de capas em disco + LRU, log rotativo, instância
  única e backup do SQLite.
- **Experiência**: atalhos, bandeja, teclas de mídia, notificações internas,
  temas e microanimações consistentes.

## Bugs e lacunas corrigidos nesta rodada

### 1. Snapshot de shuffle se perdia no restart

Antes, a fila embaralhada era salva, mas a ordem original ficava apenas na
memória. Ao reabrir o app com shuffle ligado, desligar o shuffle não tinha o que

Correção: `SessionState` agora guarda `shuffleQueue` e `shuffleIndex`.
`restore()` recupera o snapshot e `persistSession()` o salva. Além disso,
`playNow()` renova o snapshot quando uma fila nova é carregada, evitando que
“desligar shuffle” volte para a fila de outro contexto.

### 2. Capa de playlist não chegava ao celular

A capa personalizada era copiada para `%LOCALAPPDATA%\OpenfyData\covers` e
gravada como caminho local. O Android só lê `cover_url`; o resultado era
placeholder no celular.

Correção:
- nova coluna local `playlist.cover_url` (migração v5);
- upload para o bucket público `covers` do Supabase Storage em
  `{user_id}/{playlist_id}.{ext}`;
- `PushPlaylist` e `RemotePlaylist` agora carregam `cover_url`;
- o merge remoto atualiza a URL sem apagar a capa local (offline continua
  funcionando);
- `clearPlaylistCover()` limpa caminho e URL.

O teste `syncSmoke` subiu uma capa real e confirmou a URL no servidor.

### 3. Desktop não alimentava o recap da conta

O desktop gravava `play_event` local sem duração, conclusão ou estado de envio.
O Android já enviava eventos; o PC ficava de fora do recap mensal.

Correção:
- migração v6 adiciona `played_ms`, `completed`, `skipped`, `source` e `synced`;
- `recordPlay()` grava a duração da faixa e marca o evento para envio;
- `SyncEngine` envia os eventos pendentes em lote e só marca como sincronizados
  após sucesso;
- tracks do histórico também entram no push, satisfazendo a FK de `play_events`;
- `SyncResult.summary()` passou a informar as escutas enviadas.

Limitação conhecida: nesta versão o desktop registra a escuta como faixa
completa no início da reprodução. O Android já mede tempo real e skip. A próxima
evolução é instrumentar pausa/skip/fim no `Player` para o desktop ficar
igualmente preciso.

### 4. Pequenos riscos eliminados

- `setPlaylistCover()` não engole mais a exceção de cópia sem explicar: mostra
  erro quando a imagem não pôde ser usada.
- O snapshot antigo de shuffle é invalidado ao carregar uma fila nova.
- O push de tracks do sync inclui o histórico, evitando falha de FK em eventos.

## Lacunas que ainda existem

### Conta e sync

1. **Sem outbox/server_rev de verdade.** O sync ainda é LWW por `updated_at` e
   reenvia o estado local inteiro. As tabelas `sync_state` e `sync_outbox` já
   existem, mas não são usadas. Relógio errado entre PC e celular pode fazer uma
   alteração antiga vencer. Próximo passo: outbox transacional + `server_rev`
   incremental.
2. **Sem realtime.** Alterações colaborativas só aparecem no próximo sync
   (automático a cada 2 min ou manual). Supabase Realtime já está habilitado no
   schema; falta assinar playlists/itens/curtidas.
3. **Device ID do desktop é nulo nos play events.** O schema aceita, mas o recap
   não consegue separar “PC” de “celular”. Criar um UUID estável em
   `OpenfyData` e registrar em `devices` resolve.
4. **Refresh de token não é reativo em todas as chamadas.** O sync tenta renovar,
   mas chamadas isoladas (capa, share, join) podem falhar com 401 antes do próximo
   ciclo.

### Capas e mídia

5. **Storage não remove capas antigas.** Trocar a extensão (jpg → png) deixa o
   objeto anterior no bucket. Adicionar delete best-effort evita lixo.
6. **Não há upload de avatar.** O bucket `avatars` existe; a UI de perfil ainda
   não usa.
7. **Capas remotas não são baixadas para uso offline no desktop.** A URL é
   exibida via cache em memória/disco, mas o arquivo não é copiado para a pasta
   `covers`.

### Player

8. **Eventos de escuta aproximados no desktop** (ver acima).
10. **Fim de fila emenda rádio automaticamente.** É útil, mas deveria ser uma
    preferência explícita para quem usa playlists como “álbum”.
11. **Sem teste auditivo automatizado de crossfade.** O smoke valida troca,
    volume e processo, não a curva em si.

### Distribuição e engenharia

12. **Instalador sem assinatura de código.** SmartScreen pode alertar; a solução
    é certificado Authenticode.
13. **mpv de 121 MB via Git LFS.** Clone sem LFS fica sem o executável. O
    instalador já embute, mas o build a partir do fonte depende do LFS.
14. **Código duplicado entre desktop e Android** (`YouTube.kt`, modelos, regras
    de sync). O próximo grande passo estrutural é um módulo KMP `:shared`.
15. **Cobertura de UI inexistente.** Os smokes cobrem player, sync, biblioteca e
    extração; não cobrem Compose, navegação e diálogos.
16. **Sem atualização automática.** O app não consulta releases novas.

## Prioridades recomendadas

### P0 — consistência entre dispositivos

1. Outbox + `server_rev` incremental.
2. Realtime para playlists, itens e curtidas.
3. Device ID + registro do desktop em `devices`.
4. Refresh de token centralizado com retry em toda chamada autenticada.

### P1 — conta visível

5. Tela de recap mensal usando `monthly_listening`, `monthly_top_tracks` e
   `monthly_top_artists` (mês atual + anteriores).
6. UI de compartilhar/entrar por código e lista de membros da playlist.
7. Upload/remoção de capa com feedback de progresso e limpeza do Storage.
8. Perfil/avatar e feed social v1 (`activities` já existe).

### P2 — player e distribuição

9. Eventos de escuta precisos (pausa, skip, fim, tempo real).
10. Preferência para autoplay de rádio no fim da fila.
11. Assinatura Authenticode e atualização automática.
12. Extrair `:shared` (KMP) para engine, modelos e sync.

## Testes executados nesta rodada

- `compileKotlin` desktop: OK.
- `playerSmoke`:
  - crossfade real: OK;
  - shuffle reordena e restaura: OK;
  - autoplay/rádio: OK;
  - download real: OK.
- `syncSmoke` contra Supabase real:
  - login e sessão: OK;
  - upload de capa no Storage: OK;
  - `cover_url` no servidor: OK;
  - evento de escuta no recap: OK;
  - playlist, item e curtida nos dois sentidos: OK.

## Conclusão

O desktop não precisa de uma reescrita. Precisa de três camadas em cima da base
sólida que já existe:

1. **sync incremental confiável** (outbox/server_rev/realtime);
2. **recap e colaboração visíveis** (UI sobre o schema já aplicado);
3. **acabamento de distribuição** (assinatura, update, shared module).

O player, a fila e a biblioteca local já estão em nível de uso diário. O maior
retorno agora vem de fazer o PC e o celular contarem a mesma história — capa,
curtida, fila e recap — sem depender de um sync completo a cada dois minutos.


---

## Atualiza??o 0.2.4 (2026-10-28)

Esta rodada fechou o n?cleo que faltava para o desktop parecer um produto:

- **Modo DJ removido por completo** (BPM, blend, tabela `track_bpm`, botões, atalhos e
  prefer?ncias antigas). Ficou apenas o crossfade equal-power de 0?12 s.
- **Recap em aba dedicada**, com seletor de meses, minutos, execu??es, conclus?o,
  skip, top faixas, top artistas, atividade di?ria, sequ?ncia e descobertas.
  Quando h? conta, o resumo/tops v?m do Supabase e incluem PC + celular; sem conta
  ou offline, a tela mostra os dados deste PC.
- **Mais ouvidas** com ranking ponderado por curtida, rec?ncia, conclus?o, skip e
  frequ?ncia, al?m de no-repeat de faixa e diversidade de artista/?lbum.
- **R?dio reescrita**: gera??o/cancelamento de jobs, limite de 20 faixas por lote,
  deduplica??o can?nica, no-repeat por artista, ranking local e prote??o contra
  resposta atrasada que sequestrava a fila.
- **Clique na busca**: toca apenas a faixa clicada e emenda a r?dio dela. Os
  resultados s? entram na fila pelo bot?o expl?cito "Tocar resultados".
- **Autoplay configur?vel**: r?dio autom?tica ao fim de playlist/?lbum pode ser
  desligada nos Ajustes e "Encerrar r?dio" n?o ? mais revertido no fim da fila.
- **Contexto vis?vel**: a fila e o player mostram de onde vem a reprodu??o
  (playlist, ?lbum, busca, r?dio, mix, mais ouvidas).
- **Telemetria real**: cada escuta tem in?cio/fim, dura??o tocada, conclus?o, skip,
  origem e contexto; o recap e o recomendador deixam de tratar todo play como
  completo.
- **Atalhos**: `Ctrl+R` abre o Recap e os atalhos n?o roubam letras/setas de campos
  de texto em Ajustes, importa??o ou di?logos.
- **Biblioteca viva**: Home, Hist?rico e Mais ouvidas atualizam quando a faixa muda.

Testes desta rodada: `playerSmoke` (incluindo corrida de r?dio), `recommendationSmoke`,
`recapSmoke`, `featureSmoke`, `librarySmoke` e `syncSmoke` passaram.

Ainda no backlog estrutural: outbox/`server_rev`, Realtime, SMTC nativo do Windows,
assinatura Authenticode, atualiza??o autom?tica e event idempotency no Supabase.


---

## Atualiza??o 0.2.5 (2026-10-28)

- Recentes ("Ouvir de novo") agora s?o sementes de r?dio: clicar toca s? a faixa
  e emenda a r?dio dela, sem transformar o hist?rico em fila.
- O menu de adicionar ? playlist sempre oferece "Criar nova playlist", inclusive
  quando ainda n?o existe nenhuma; o bot?o do player abre o di?logo direto nesse caso.
- Tela cheia do player ganhou fundo em gradiente extra?do das cores dominantes da
  capa, com op??o de desligar.
- Ajustes > Apar?ncia: cinco paletas (Openfy, Violeta, Ros?, ?mbar, Gelo) e layout
  do player Central/Lateral, ambos persistidos no SQLite.
- `docs/ROADMAP-SPOTIFY.md` registra a avalia??o de fun??es do Spotify priorizadas
  para o Openfy.

Build 0.2.5 validado com `compileKotlin`, abertura real do app, troca de paleta
(Violeta), layout Lateral e fundo pela capa.

---

## Atualização 0.2.7 (2026-10-28)

- Tooltips de ícones agora ficam ancorados acima do botão, com 8 dp de
  afastamento, em vez de seguir o cursor e cobrir o próprio botão.
- O vídeo/clipe foi avaliado: o extrator já devolve URL de vídeo, mas o player
  atual é audio-only (mpv `--no-video`), usa dois processos A/B para crossfade e
  não tem superfície de vídeo. Exibir vídeo inteiro com áudio ou um Canvas
  exigiria uma segunda pipeline, sincronização e uma janela nativa; ficou para
  uma rodada dedicada, sem aumentar o peso do instalador agora.
