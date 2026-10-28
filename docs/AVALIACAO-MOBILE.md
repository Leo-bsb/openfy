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
