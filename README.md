# Openfy

Tocador de música pessoal para Windows. Fonte única: YouTube Music via InnerTube
(sem anúncios). Interface desktop em Compose, áudio no mpv.

## Como funciona

```
Compose UI
   │
   ├── AppController ── Player ── mpv.exe  (named pipe JSON IPC)
   │                      │
   │                      └── http://127.0.0.1:<porta>/t/<videoId>
   │                                   │
   └── YouTubeEngine ──────────────────┘  (busca + extração InnerTube)
```

O mpv nunca recebe uma URL do Google. Quem resolve a faixa é o `StreamServer`
local, que reextrai quando a assinatura expira. Por isso URL vencida não é um
caso de erro.

O IPC usa **duas conexões** com o named pipe do mpv: uma só para ler eventos e
outra só para enviar comandos. Um único handle para ler e escrever trava o
`write` enquanto a leitura está bloqueada — era o bug em que clicar numa faixa
não tocava e congelava o app.

Os comandos entram numa fila e uma thread dedicada escreve e **drena a resposta**
de cada um. Sem isso o canal de comandos enchia de respostas não lidas, o mpv
parava de ler e a UI travava depois de alguns minutos de uso.

## Estado atual

- [x] Busca no catálogo do YouTube Music com filtros (músicas, artistas, álbuns, playlists, vídeos)
- [x] Perfil oficial do artista em destaque, com populares, álbuns, singles e seções relacionadas
- [x] Playlists e álbuns públicos, com carregamento das listas longas por continuação
- [x] Explorar com playlists em destaque, lançamentos e playlists da comunidade
- [x] Rádio por faixa e por artista, com autoplay e prefetch da próxima página
- [x] Extração de áudio via `innertubex-desktop:v0.11.0` (sem PoToken/WebView)
- [x] Streaming com Range/seek através do servidor local
- [x] Fila, aleatório, repetir (off/all/one), volume
- [x] Curtidas, histórico, playlists locais (SQLite)
- [x] Criar/renomear/excluir playlist com confirmação
- [x] Navegação voltar/avançar com pilha de telas (botões + `Alt+←`/`Alt+→`)
- [x] Sessão retomada: fila, faixa, posição, volume, aleatório e repetir persistem
- [x] Atalhos de teclado (espaço, setas, `Ctrl+←/→`, `L`, `S`, `R`, `Q`, `Ctrl+F`, `Esc`)
- [x] Fila editável: remover, reordenar, limpar próximas, salvar como playlist e desfazer
- [x] Busca: recentes, sugestões (ao digitar), top result, carregar mais, retry e skeleton
- [x] Banner offline e cache de capas em disco (com LRU em memória)
- [x] "Em alta" (charts): as músicas em alta do YouTube Music
- [x] Downloads: baixar faixa/álbum/playlist e tocar offline
- [x] Download automático em segundo plano (mais ouvidas + similares), com interruptor
- [x] Ações de playlist/download na barra inferior, na tela do player e em cada item da fila
- [x] Transições e microanimações (tela cheia, troca de telas, fila, avisos, hover)
- [x] Bandeja do Windows (minimizar/fechar continua tocando) + teclas de mídia do teclado
- [x] Sair pelo botão de energia/bandeja sem travar (shutdown fora da thread da UI)
- [x] Interface no estilo Spotify (preto + verde-água + ciano), com tela cheia do player
- [x] Adicionar à playlist na barra inferior e na tela do player (com criar playlist já adicionando a faixa)
- [x] Equalizador de 5 bandas com presets (Flat/Pop/Rock/Graves/Vocal), persistido
- [x] Instância única, log rotativo (`openfy.log.1`) e crash handler
- [x] Tela de Ajustes (qualidade, crossfade, downloads, janela e dados)
- [x] Capa personalizada nas playlists (ou a capa da primeira faixa)
- [x] Transição entre faixas configurável (crossfade equal-power, 0–12 s)
- [x] Qualidade de áudio Alta/Econômica
- [x] Schema pronto para sync (`updated_at` + tombstones)
- [x] "Seu mix" multi-semente (até 3 sementes de curtidas/recentes, faixas intercaladas)
- [x] Autoplay: busca toca e emenda a rádio; playlist/álbum/mix vai até o fim e então entra na rádio
- [x] Importar playlist/álbum do YouTube Music por link (sem login)
- [x] Importar CSV do Exportify (Spotify) casando faixas no catálogo
- [x] Backup JSON de playlists e curtidas (exportar/importar)
- [x] Letras sincronizadas (LRCLIB) na tela do player
- [x] Timer para dormir (15/30/45/60 min)
- [x] Estatísticas no Início (execuções, horas ouvidas, artista top)
- [x] Logo própria no app, na bandeja, na janela e no ícone do .exe/atalho
- [x] Estabilidade do IPC do mpv: fila de comandos + drenagem das respostas (evita travar a UI)
- [x] Instaladores Windows com mpv embutido (.exe e .msi)
- [ ] Login próprio + sincronização entre dispositivos (Supabase, próximo)
- [ ] OAuth Spotify/Google para importar playlists da conta (próximo)

## Busca, artista e rádio

A busca usa os filtros do próprio YouTube Music (`params` do InnerTube), então
karaokê, podcasts e vídeos aleatórios ficam fora da aba de músicas. A aba
padrão é **Músicas**; acima dos resultados aparece uma faixa de artistas
oficiais. As abas **Artistas**, **Álbuns**, **Playlists** e **Vídeos** trocam o
filtro sem refazer a digitação.

O **Explorar** combina buscas fixas de playlists em destaque (hits, pop,
sertanejo, rock, rap, funk, MPB) com lançamentos e playlists da comunidade.
As sementes fixas evitam que a tela mude de ordem a cada abertura.

A **rádio** usa `next` com `RDAMVM<videoId>` (faixa) ou `RDEM...` (artista).
A fila já nasce com ~20 faixas e busca a próxima página quando chega perto do
fim. Playlists longas repetem a primeira página em algumas continuações; o
controlador ignora o token quando nenhuma faixa nova é acrescentada.

## Atalhos, fila e sessão

Atalhos globais (funcionam mesmo com um botão focado; pausam enquanto você
digita na busca ou num diálogo):

| Tecla | Ação |
| --- | --- |
| `Espaço` | tocar/pausar |
| `←` / `→` | voltar/avançar 5 s |
| `Ctrl+←` / `Ctrl+→` | faixa anterior/próxima |
| `L` | curtir a faixa atual |
| `S` | aleatório |
| `R` | repetir (off → all → one) |
| `Q` | abrir/fechar a fila |
| `Ctrl+P` | abrir/fechar a tela do player |
| `Ctrl+F` | focar a busca |
| `Ctrl+R` | abrir o Recap |
| `Esc` | fechar o player/fila (ou voltar de tela) |

A **fila** é editável: clicar numa faixa toca na hora; os botões ao lado
removem ou reordenam; "Limpar próximas" descarta o que vem depois da faixa
atual; "Salvar" guarda a fila como playlist. Toda remoção pode ser desfeita
pelo botão "Desfazer" do aviso.

A **sessão** (fila, faixa, posição, volume e modos) é salva no SQLite e
retomada pausada na próxima abertura, junto com a última tela e o tamanho e
posição da janela. As capas ficam em disco (cache em duas camadas: memória com
LRU + arquivos), então a segunda abertura não baixa as imagens de novo.

## Downloads, bandeja e mídia

**Downloads**: no menu (⋮) de qualquer faixa escolha "Baixar"; em álbum ou
playlist há um botão "Baixar" que salva tudo. As faixas vão para
`%LOCALAPPDATA%\OpenfyData\downloads\` e passam a ser tocadas do disco — inclusive
sem internet. A tela **Downloads** lista o que já está salvo; "Remover download"
apaga o arquivo.

A barra inferior, a tela do player e cada item da fila também têm o botão de
baixar/remover e de adicionar à playlist (o menu da fila inclui as duas ações).

Com **Download automático** ligado (interruptor na tela Downloads), o app baixa
em segundo plano, sem avisar, as faixas mais ouvidas do histórico e algumas
similares — a partir da rádio da mais tocada — com limite de quantidade.

Para encerrar de verdade, use o **botão de energia** no topo ou "Sair" na
bandeja. Fechar pelo X minimiza para a bandeja (com um aviso); o áudio continua.

**Bandeja e teclas de mídia**: fechar a janela minimiza para a bandeja e o áudio
continua. O ícone na bandeja tem tocar/pausar, próxima, anterior, mostrar e
sair. As teclas de mídia do teclado (play/pause, próxima, anterior) funcionam
globalmente, mesmo com o Openfy em segundo plano. Se outro app já tiver tomado
as teclas, o registro é ignorado sem quebrar nada.

**Em alta** (charts): as músicas em alta agora, tiradas da playlist de
tendências do YouTube Music (a página de charts só traz artistas e cards; o app
navega até a playlist para montar a lista).

## Interface, player e equalizador

A interface segue o padrão de tocador que todo mundo já conhece: barra lateral
com navegação e biblioteca, conteúdo no centro e barra de reprodução embaixo.
A paleta é preto profundo com verde-água e ciano; a logo é um traço de dois
laços com cabeça de cobra, usada na sidebar, na bandeja e como ícone da janela.

- **Tela do player**: clique na faixa da barra inferior (ou na seta para cima,
  ou `Ctrl+P`) para abrir a tela cheia com capa grande, título, progresso,
  curtir, adicionar à playlist, equalizador, volume e fila. `Esc` fecha.
- **Adicionar à playlist**: botão de playlist+ na barra inferior e na tela do
  player, com a lista de playlists e "Nova playlist" (que já cria e adiciona a
  faixa atual).
- **Equalizador**: 5 bandas (60 Hz a 14 kHz) aplicadas ao mpv via filtros do
  FFmpeg, com presets e estado salvo. Abra pelo ícone de sliders (barra e player).
- **Rótulos**: os ícones mostram o nome/atalho ao passar o mouse (aleatório,
  repetir, equalizador, letras, timer, fila, download, ajustes e navegação).
- **Estados visíveis**: botões ativos usam preenchimento sólido (não só cor) e os
  filtros de busca mostram um check; assim o estado ligado/desligado continua
  claro para quem tem daltonismo. A troca de tela na busca rola junto com os
  resultados: o perfil do artista e o destaque não ficam mais fixos cobrindo a lista.
- **Seu mix**: usa as faixas mais ouvidas, curtidas e recentes como sementes e intercala as rádios sem deixar um artista dominar.
  intercala as faixas das rádios de cada uma, sem repetir. Mais dados locais =
  melhor; sem servidor.
- **Autoplay**: clicar num resultado de busca toca a faixa e emenda a r?dio dela. Ao terminar uma playlist/?lbum, a r?dio autom?tica pode ser ligada ou desligada nos Ajustes.
  dela. Ao tocar um álbum, playlist ou mix, o app respeita a lista até o fim e
  só então entra na rádio da última faixa.
- **Rádio e sucessão**: clicar numa faixa da busca toca apenas aquela faixa e emenda a rádio dela; os resultados da busca não viram fila. O botão "Tocar resultados (contexto)" é o contexto explícito.
- **Recap**: aba dedicada com seletor de mês, minutos, execuções, conclusão, skip, top faixas/artistas, atividade diária e descobertas. Com conta, o resumo e os tops incluem PC + celular.
- **Mais ouvidas**: ranking ponderado por curtidas, recência, conclusão e skip, com no-repeat de faixa e diversidade de artista. Toca em contexto ou aleatório.
- **Seu mix**: usa as faixas mais ouvidas, curtidas e recentes como sementes e intercala as rádios sem deixar um artista dominar.
- **R?dio autom?tica**: ao terminar playlist/?lbum, continua a r?dio da ?ltima faixa; pode ser desligada nos Ajustes.


## Importar, letras e estatísticas

- **YouTube Music por link**: no botão de importar (topo), cole a URL de uma
  playlist ou álbum público. O app carrega as continuações e cria a playlist local.
- **Spotify sem login**: exporte suas playlists com o Exportify (CSV) e importe.
  Cada faixa é casada por título, artista e duração no catálogo do YouTube Music.
- **Backup**: exporte/importe um JSON com playlists e curtidas (metadados, sem áudio).
- **Compartilhar playlist**: no cabeçalho da playlist, "Compartilhar" gera um
  arquivo `.openfyplaylist` para enviar a outra pessoa. No importador, "Playlist
  Openfy" abre esse arquivo e recria a playlist com os mesmos videoIds.
  Com conta, o mesmo diálogo também gera um código/link para convidar outras
  pessoas a editar a playlist (backend Supabase ja aplicado).
- **Letras**: botão de letras na tela do player; busca no LRCLIB, prioriza LRC
  sincronizado e destaca a linha atual. Sufixos como "(Official Video)" são limpos.
- **Timer**: 15/30/45/60 min na tela do player; pausa ao terminar.

A conta própria e o sync via Supabase já estão ativos. OAuth Spotify/Google
para importar a conta ainda não; use o CSV do Exportify.

## Ajustes e transi??o

- **Ajustes** (engrenagem no topo): qualidade Alta/Econômica, transição entre
  faixas, equalizador, download automático, fechar para a bandeja,
  backup, cache e pasta de dados.
- **Capas de playlist**: no cabeçalho da playlist, "Capa" > escolher imagem. A imagem é copiada para `%LOCALAPPDATA%\OpenfyData\covers\`, sobe para o Storage da conta e aparece no celular; sem capa própria, usa a arte da primeira faixa.
- **Transição**: crossfade real de 0–12 s com dois players mpv e curva
  equal-power: no meio da transição as duas faixas ficam audíveis, sem o "buraco"
  do fade linear. Qualquer intera??o (pausar, seek, trocar de faixa) cancela o fade.
- **Aleatório**: os botões "Aleatório" dos cabeçalhos já iniciam a reprodução na
  hora; a seta de aleatório da barra liga o modo e, se nada estiver tocando,
  começa o contexto aberto (playlist, álbum, artista, curtidas ou busca).
- **Qualidade**: Alta usa o teto do extrator; Econômica reduz o consumo.
- **Sync**: conta própria + Supabase ativos para playlists, itens, curtidas e eventos. O merge é LWW por `updated_at`; outbox + `server_rev` e realtime são os próximos passos.
- **Confiabilidade**: instância única (a segunda abertura foca a janela atual),
  log rotativo, crash handler registrando stack traces e backup consistente do
  banco a cada encerramento.

## Conta e sync com o celular

O sync é opcional: sem login, o Openfy continua local. Com conta, o PC e o
Android compartilham playlists, itens, curtidas e eventos de escuta via Supabase.

- No PC: **Ajustes > Conta e sincronização** > entrar/criar conta e
  **Sincronizar agora**.
- No Android: login, playlists, busca, curtidas, fila, ajustes de crossfade e
  player Media3. Criar uma playlist no celular e sincronizar no PC traz a
  playlist para a sidebar; criar no PC e sincronizar no celular traz para o app.
- Capas de playlist personalizadas no PC sobem para o Storage público e
  aparecem no Android; o PC mantém a cópia local para uso offline.
- Eventos de escuta do PC e do Android alimentam o recap mensal da conta.
- O app Android ainda não tem reprodução em background/notificação, downloads
  ou equalizador. Essas peças entram nas próximas etapas.
- Nesta versão o merge é LWW por `updated_at`. O próximo passo é trocar por
  outbox + `server_rev` para conflitos offline mais seguros.

## Instalar

Instaladores (usuário atual, sem admin):

```
dist\Openfy-0.2.4.exe
dist\Openfy-0.2.4.msi
dist\Openfy-Android-0.2.6.apk
```

Desenvolvimento: https://github.com/Leo-bsb/openfy/releases  
Desktop 0.2.4: https://github.com/Leo-bsb/openfy/releases/tag/v0.2.4  
Android 0.2.6: https://github.com/Leo-bsb/openfy/releases/tag/v0.2.6-android

Versão portátil: a pasta `build\compose\binaries\main\app\Openfy\` inteira,
executando `Openfy.exe`. Os instaladores em `dist\` são cópias dos artefatos
gerados em `build\compose\binaries\main\`.

## Build

Requisitos: JDK 21 e `mpv.exe` em `resources-dist\windows\` (o binário não é
versionado por ter ~121 MB; veja `resources-dist/windows/README.md`).

```powershell
$env:JAVA_HOME="D:\scripts\openfy\.tools\jdk21"
.\gradlew.bat createDistributable   # pasta portátil
.\gradlew.bat package               # .exe + .msi
```

Saídas:

- `build\compose\binaries\main\app\Openfy\Openfy.exe`
- `build\compose\binaries\main\exe\Openfy-0.2.4.exe`
- `build\compose\binaries\main\msi\Openfy-0.2.4.msi`

Rodar em desenvolvimento:

```powershell
.\gradlew.bat run
```

## Testes headless

```powershell
.\.tools\gradle-dist\bin\gradle.bat smoke        # busca -> extração -> HTTP local
.\.tools\gradle-dist\bin\gradle.bat playerSmoke  # Player -> IPC do mpv (volume 0)
.\.tools\gradle-dist\bin\gradle.bat featureSmoke # busca filtrada, artista, coleções, explorar e rádio
.\.tools\gradle-dist\bin\gradle.bat librarySmoke # importador, backup JSON e backup do banco
.\.tools\gradle-dist\bin\gradle.bat syncSmoke    # pull/push real de playlists no Supabase
.\.tools\gradle-dist\bin\gradle.bat extract -Pid=<videoId>  # mede uma extração
.\.tools\gradle-dist\bin\gradle.bat probe        # salva respostas cruas em %TEMP%\openfy-probe
```

O `playerSmoke` prova o caminho real: carrega, pausa, retoma, faz seek, troca de
faixa e confirma que uma faixa inválida gera erro (em vez de ficar travado).
O `featureSmoke` cobre a busca com filtros e continuação, o perfil de artista,
álbum/playlist públicos, o Explorar, as rádios, as sugestões, as letras (LRCLIB)
importador (URL/CSV/match), o backup JSON, a playlist compartilhada
(`.openfyplaylist`) e o backup consistente do banco (`VACUUM INTO`). O `probe` é
só uma ferramenta de diagnóstico: grava JSONs brutos para inspecionar tokens e
formatos novos.

> O SLF4J escreve no stderr e o PowerShell pode marcar o comando como erro
> mesmo com `BUILD SUCCESSFUL`; considere aprovado quando aparecer `TUDO OK`.

## Dados e diagnóstico

- Banco: `%LOCALAPPDATA%\OpenfyData\openfy.db` (SQLite, WAL).
- Backup automático: `%LOCALAPPDATA%\OpenfyData\openfy-backup.db`, regravado a
  cada encerramento com `VACUUM INTO` (cópia consistente, mesmo com WAL ativo).
- Log do app: `%LOCALAPPDATA%\OpenfyData\openfy.log` (recriado a cada start).
  Registra busca, extração (tempo/erro), requisições do stream, comandos e IPC.
- Log do mpv: `%LOCALAPPDATA%\OpenfyData\mpv.log` (recriado a cada start).
- Capas em cache: `%LOCALAPPDATA%\OpenfyData\artwork\` (uma imagem por URL, com hash).
- Downloads: `%LOCALAPPDATA%\OpenfyData\downloads\` (uma faixa por arquivo).

Os dados ficam **fora da pasta de instalação** (`%LOCALAPPDATA%\Openfy`). Assim,
atualizar, reparar ou desinstalar o app não apaga curtidas, playlists, histórico,
capas e downloads. Na primeira abertura, o Openfy migra automaticamente o banco,
capas, cache e downloads da pasta antiga. Apagar o `.db` zera curtidas, playlists,
histórico, buscas recentes e a sessão.

## Estrutura

```
src/main/kotlin/com/openfy/
  Main.kt              composição da aplicação, janela, shutdown
  core/                modelos, controlador de telas, player state, log
  data/                SQLite, InnerTube, servidor de stream
  player/              mpv (IPC) e fila
  ui/                  Compose (tema + telas)
  dev/                 verificações headless (não usadas pelo app)
```

### Regra de ouro

`innertubex` é dependência externa, não código nosso. Nada de patch no
`data/` do motor: correção vem de atualizar a versão do artefato.
