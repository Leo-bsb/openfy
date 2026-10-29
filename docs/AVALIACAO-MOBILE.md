# Avaliação do Openfy Mobile — estado e próximos passos

Data: 2026-10-26. Base: Android 0.2.2; desktop 0.2.1.

## Resumo

O Android deixou de ser só login/sync e virou um tocador funcional:

- conta + sync com o desktop;
- busca no YouTube Music via InnerTubeX;
- player Media3/ExoPlayer;
- capas com Coil;
- crossfade real de 6 s com dois players;
- playlists sincronizadas e reprodução;
- eventos de escuta no Supabase;
- APK assinado.

Ele ainda **não é a paridade completa** com o desktop. A diferença maior hoje é
arquitetura de reprodução: o player vive no `ViewModel` da tela. Isso funciona
enquanto o app está aberto, mas não é um player de música de verdade em
background.

## Matriz de paridade

| Área | Desktop | Android 0.2.2 | Próximo passo |
|---|---|---|---|
| Conta/sync | Sim | Sim | Outbox + server_rev |
| Busca de músicas | Sim, + artistas/álbuns/playlists | Só músicas | Abas/artista/álbum |
| Capas | Sim, cache LRU | Sim (Coil) | Capa de playlist vinda do Storage |
| Player | mpv A/B | ExoPlayer A/B | MediaSessionService |
| Crossfade | 0–12 s configurável | 6 s fixo | Configuração |
| EQ | 5 bandas | Não | `Equalizer`/`DynamicsProcessing` |
| Letras | LRCLIB | Não | Painel de letras |
| Sleep timer | Sim | Não | Timer + alarme |
| Fila | Editar/limpar/desfazer | Só visual | Tela de fila |
| Curtidas | Sim | Não no mobile | Botão + sync |
| Adicionar à playlist | Sim | Não no mobile | Menu |
| Remover/reordenar | Sim | Não | API + UI |
| Downloads/offline | Sim | Não | WorkManager + local DB |
| Importar YTM/CSV/backup | Sim | Não | SAF + telas |
| Compartilhar/colaborar | Sim | Não | Link/código + membros |
| Social/recap | Backend pronto, UI não | Não | Perfil/feed/recap |
| Ajustes | Sim | Quase nenhum | Tela de ajustes |
| Background/notificação | Bandeja/mpv | Não | Serviço foreground |
| Sessão restaurada | Sim | Não | Persistir fila/posição |
| Android Auto/widgets | — | Não | Depois |

## O que está bem resolvido

- InnerTubeX Android funcionando para busca e extração.
- Media3 com capas e controles básicos.
- Crossfade com dois players no código (precisa validação auditiva no aparelho).
- Sync PC ↔ Android validado (`syncSmoke` + teste real).
- Play events reais chegando ao Supabase.
- APK release assinado e release no GitHub.
- Identidade visual consistente.

## Pontos que precisam de ajuste

### 1. Background playback (prioridade máxima)
Hoje o player mora no `AuthViewModel`. Ao sair do app, o Android pode pausar/matar
o processo. Precisa:

- `MediaSessionService` ou foreground service;
- notificação com play/pause/next/previous;
- audio focus e "becoming noisy";
- lockscreen/Bluetooth;
- singleton de playback fora da tela.

Isso provavelmente vem junto com uma pequena refatoração: `PlaybackEngine` global
+ serviço; a UI vira um cliente do engine.

### 2. Crossfade
O código já tem dois ExoPlayers e rampa equal-power de 6 s, mas:

- validar em fone/Bluetooth;
- conferir audio focus dos dois players;
- tornar o tempo configurável (0–12 s);
- garantir que pausar/seek/next cancelem o fade;
- testar troca de player com a tela bloqueada.

### 3. Capas
Busca, mini player e tela cheia já usam Coil. Playlists usam `cover_url`, mas o
desktop ainda salva capa local e não sobe para o Supabase Storage. Precisa:

- upload da capa no Storage;
- fallback para a arte da primeira faixa quando não houver capa própria;
- cache de imagem no disco (Coil já faz).

### 4. Biblioteca local/cache/offline
O Android hoje depende da rede e do Supabase. Para ficar desktop-like:

- banco local (Room ou SQLDelight);
- cache de metadados e capas;
- fila/sessão persistidas;
- downloads com WorkManager;
- reprodução offline;
- sincronização offline/outbox.

### 5. Reuso de código
`YouTube.kt`, modelos e parte do sync estão duplicados entre desktop e Android.
O caminho ideal é um módulo KMP `:shared` para modelos, busca e sync; o player
continua nativo por plataforma.

### 6. Segurança
Sessão Android ainda em SharedPreferences privadas. Migrar para
EncryptedSharedPreferences/Keystore antes de distribuição pública.

### 7. URL direta do stream
O Android entrega a URL extraída direto ao ExoPlayer. Se a URL expirar durante
uma pausa longa/seek, o player pode falhar. Opções: reextrair no erro ou criar
um proxy local, como o `StreamServer` do desktop.

### 8. Sessão/estado
Ao fechar o app, fila/posição/modos são perdidos. Persistir em banco e restaurar
pausado, como no desktop.

## Ordem recomendada

1. **PlaybackEngine + foreground service/MediaSessionService**.
2. **Crossfade configurável + validação em aparelho**.
3. **Curtidas, adicionar à playlist, fila e sessão restaurada**.
4. **Tela de ajustes** (crossfade, qualidade, download, conta, cache).
5. **Banco local + downloads/offline**.
6. **Importar/compartilhar/colaborar no Android**.
7. **Recap e social**.

## Como testar o 0.2.2

1. Instalar `Openfy-Android-0.2.2.apk`.
2. Criar/logar a conta.
3. Buscar uma música e tocar.
4. Conferir capa no mini player e na tela cheia.
5. Deixar a faixa chegar perto do fim: deve preparar a próxima e cruzar 6 s.
6. Tocar uma playlist sincronizada do PC.
7. Bloquear a tela: **hoje o áudio pode parar** — é o próximo bloco a resolver.

## Observação

O crossfade 0.2.2 foi compilado e o APK assinado, mas o celular desconectou antes
da validação auditiva final. O próximo teste no aparelho deve confirmar a
transição e o comportamento de pausa/seek.


---

## Atualiza??o 0.2.5 (2026-10-27)

### Entregue nesta rodada

- Detalhe de playlist com lista de faixas, capa, play-all e estado de carregamento.
- Curtir/descurtir faixas com atualiza??o otimista e sincroniza??o com o Supabase.
- Aba **Curtidas** com as faixas curtidas na conta.
- Adicionar faixa ? playlist, remover faixa e criar playlist.
- Menu por faixa com **Tocar em seguida**, **Adicionar ? fila** e **Adicionar ? playlist**.
- Tela de fila no player com tocar, remover e indicar a faixa atual.
- Tela de ajustes com conta, sincroniza??o, sa?da e crossfade configur?vel (0?12 s).
- Crossfade agora l? e grava a prefer?ncia local, em vez de 6 s fixos.
- Retry autom?tico de token em chamadas autenticadas (401 -> refresh -> repete).
- Capa de playlist vinda do Storage aparece no Android via `cover_url`.

### Validado

- `assembleRelease` do APK 0.2.5.
- APIs mobile contra o Supabase real: criar playlist, inserir item, remover item
  (soft delete), curtir e descurtir.
- Upload de capa e `cover_url` j? validados pelo `syncSmoke` do desktop.

### Ainda falta

1. Background playback real (`MediaSessionService`, notifica??o, lockscreen,
   Bluetooth). ? a lacuna mais importante para paridade com o desktop.
2. Persistir fila/posi??o entre aberturas.
3. Banco local/offline e downloads.
5. Recap mensal na UI (dados j? chegam ao Supabase).
6. Importar/compartilhar/colaborar no Android (backend e desktop prontos).
7. Capa pr?pria de playlist pelo celular.
8. Mover sess?o para Keystore/EncryptedSharedPreferences.
9. M?dulo KMP `:shared` para engine, modelos e sync.


---

## Atualiza??o 0.2.6 (2026-10-28)

- Clique num resultado de busca toca **somente** a faixa clicada e emenda a r?dio
  dela; os resultados da busca n?o viram mais a fila.
- A r?dio do Android busca a pr?xima p?gina perto do fim, deduplica por faixa e
  evita repetir artista na janela recente.
- A fila ? mantida ao avan?ar/voltar faixas e a resposta atrasada de uma r?dio
  antiga ? descartada por gera??o.
- APK 0.2.6 (versionCode 8) assinado.

Ainda falta o bloco de background playback/MediaSessionService, banco local,
downloads e recap nativo no Android.

---

## Atualização 0.2.7 (2026-10-28)

- Home do Android ganhou **“Ouvir de novo”**: as últimas faixas tocadas ficam
  salvas no aparelho e o clique toca somente aquela faixa, emendando a rádio
  dela; o histórico não vira fila.
- **“Mais ouvidas no mês”** usa a view `monthly_top_tracks` da conta e toca a
  lista em ordem, como no desktop.
- O diálogo de adicionar à playlist sempre oferece **“Criar nova playlist”**,
  inclusive quando ainda não existe nenhuma; a playlist é criada já com a faixa
  e aparece no desktop na sincronização seguinte.
- **Aparência** com as cinco paletas do desktop, layout do player
  Central/Lateral, switch de gradiente pela capa e persistência entre aberturas.
- A biblioteca é recarregada ao voltar ao app (throttle de 30 s) e o desktop
  sincroniza ao abrir com a sessão salva. Playlists criadas no PC aparecem no
  celular e vice-versa, sempre na mesma conta.
- APK 0.2.7 (versionCode 9) assinado com a keystore Openfy.
  SHA-256: `41A22B1D18A76F90476749C0557F2E0C13E6BA19FBAA6D40955971F9E7BEF0CC`.

Ainda faltam no Android: background playback/MediaSessionService, banco local e
modo offline, downloads, recap nativo, importar/compartilhar/colaborar e capa
própria de playlist pelo celular.

---

## Atualização 0.3.0 (2026-10-28)

### Entregue nesta rodada

- **Reprodução em background** com serviço foreground, notificação de mídia,
  MediaSession, controles de play/pause/anterior/próxima, lockscreen e Bluetooth.
- **Fila e posição restauradas** ao abrir o app, pausadas na posição salva.
- **Letras** via LRCLIB, com LRC sincronizado e destaque da linha atual.
- **Timer para dormir** de 15/30/45/60 minutos.
- **Equalizador** de 5 bandas com presets Flat/Pop/Rock/Graves/Vocal, persistido.
- **Recap mensal** na aba própria, com minutos, execuções, faixas, top faixas e
  top artistas da conta.
- **Capa de playlist pelo celular**, enviada ao Storage e sincronizada.
- **Compartilhar/entrar por código** e **importar playlist/álbum do YouTube
  Music por link**.
- Backup do app desativado e regras de extração excluem a sessão; a sessão
  continua em SharedPreferences privadas e deve migrar para Keystore.
- APK 0.3.0 (versionCode 10) assinado com a keystore Openfy.
  SHA-256: `A86C74D5981B2CD5F41EE96713CC0E0C16A6A2E46EEAAA324A0679E883C4711C`.

### Validado no Galaxy A15

- Instalação sobre a 0.2.7 e login existente.
- Serviço foreground ativo, notificação 711, MediaSession PLAYING, pause/play
  pelo sistema e áudio com o app em background.
- Letras de "Limite" carregadas e sincronizadas.
- Timer abrindo as opções e EQ aplicando o preset Graves.
- Recap carregando 19 minutos, 7 execuções e 3 faixas.
- Criar playlist, compartilhar código/link, abrir o seletor de capa e importar
  pelo botão novo.
- Fila restaurada pausada em 1:33 depois de forçar a parada do app.

### Ainda falta / próximos passos

1. Banco local/offline, downloads e reprodução sem rede.
2. Importar CSV do Exportify e backup JSON no Android.
3. Módulo KMP `:shared` para engine, modelos e sync.
4. Migrar a sessão para EncryptedSharedPreferences/Keystore.
5. Servidor: restringir a leitura de `playlists.share_code` (hoje o select é
   amplo para playlists públicas) e expor o código só ao dono/membro por RPC.
6. Reprodução em background depende do processo vivo; se o app for removido dos
   recentes, o serviço para. Um `MediaSessionService` com o player dentro do
   serviço é a evolução.
7. Vídeo/clipe no player ainda não implementado.

---

## Atualização 0.3.1 (2026-10-28)

- **Perfil do artista** no Android: a busca agora mostra artistas acima das
  músicas e abre a página com foto, ouvintes mensais, descrição, populares,
  álbuns/singles e rádio do artista. O nome do artista no player e nas linhas de
  faixa também abre o perfil.
- **Álbuns e playlists remotas** abertos a partir do perfil do artista, com
  capa, lista de faixas e play-all.
- **Importar do Spotify por link**: playlists públicas do Spotify são lidas
  pela página de embed, casadas com o catálogo do YouTube Music e salvas na
  conta. O diálogo de importar aceita YouTube Music ou Spotify.
- O importador mostra o progresso do casamento e avisa quantas faixas foram
  encontradas.
- APK 0.3.1 (versionCode 11) assinado em 28/10/2026.
  SHA-256: `0E8A31D77BBF707D259ED60CD6659AE80FCB66CF2CFA7B0AB4A82DA398D6FEF5`.

### Testado no Galaxy A15

- Busca "Billie Eilish": seção de artistas, perfil completo, populares, álbuns
  e rádio.
- Clique no nome do artista no player abre o perfil.
- Importação da playlist pública "Today's Top Hits" do Spotify: 49 de 50
  faixas casadas e playlist criada com as faixas no Supabase.

### Limites

- Importação do Spotify funciona com playlists **públicas** por link. Playlists
  privadas exigem OAuth/Exportify; o CSV ainda não está no Android.
- A página de embed do Spotify pode mudar; se isso acontecer, a importação falha
  com aviso e não cria playlist vazia.

---

## Atualização 0.4.0 (2026-10-28)

- **Cache local da Home**: perfil, playlists, curtidas, top tracks e recentes
  ficam em snapshot local e abrem sem rede, com banner e data do snapshot.
- **Downloads de faixas e playlists**: arquivo `.part`, progresso, cancelamento,
  retry de URL expirada, limite de 512 MB–10 GB, remoção e card em Ajustes.
- **Reprodução local**: faixas baixadas tocam do armazenamento interno, no
  player, na fila restaurada e no crossfade; o preload também usa o arquivo local.
- **Segurança**: convite de playlist via RPC `playlist_share_code`; a migration
  `0002_security_hardening.sql` restringe o SELECT de `share_code` e deve ser
  aplicada **depois** de publicar desktop 0.2.8 e Android 0.4.0.
- Testes: 25 no desktop (importadores, BuildInfo e UpdateChecker) e 26 no
  Android (DownloadStore, AudioDownloader, HomeCache, Importer e SpotifyClient);
  CI no GitHub Actions.
- APK 0.4.0 (versionCode 12) assinado.
  SHA-256: `A2477959736425B8852BE9FA47E9FAA330FEE594FC5204D7BA28BD754F47120B`.

### Limitações conhecidas

- Downloads são do aparelho e continuam após sair da conta; snapshots da Home e
  playlists abertas são limpos no logout.
- Eventos de reprodução gerados offline (histórico/recap) não entram em fila:
  são descartados até a próxima carga online.
- Downloads param se o processo do app for encerrado; ainda não há WorkManager.
- Quando o servidor não envia `Content-Length`, a barra fica indeterminada.
- A 0.4.0 não foi validada em aparelho após as correções de robustez; o último
  teste real foi na 0.3.1.

Ainda faltam: downloads em background com WorkManager, importar CSV/backup JSON,
MediaSessionService com player dentro do serviço, KMP e Keystore.

---

## Atualização 0.4.1 (2026-10-28)

- **Aleatório**: botão no player mantém a faixa atual e embaralha o restante;
  desligar restaura a ordem original. O estado persiste entre sessões.
- **Repetir**: off → toda a fila → uma faixa, com ícone e estado persistidos.
- **Reordenar a fila**: setas de subir/descer no diálogo da fila, com o índice
  da faixa atual acompanhando a mudança.
- **Tocar em seguida / ao fim**: o menu ⋮ das faixas tem "Tocar em seguida" e
  "Adicionar ao fim da fila"; o diálogo da fila explica o caminho.
- **Robustez de downloads**: índice ilegível não apaga mais os áudios; gravação
  do índice com fsync e rollback; cancelamento desconecta a conexão; download
  na fila fica visível/cancelável; sessão revogada limpa caches locais.
- Testes: 27 no desktop e 33 no Android (inclui QueueLogic, índice corrompido,
  corpo truncado e seleção de release). CI verde.
- APK 0.4.1 (versionCode 13) assinado.
  SHA-256: `A7B097B943C081812C61CFA27D55E6D35AD156883BB81208746F5EB4585E1558`.

A 0.4.1 ainda não foi validada em aparelho; o último teste real foi na 0.3.1.
