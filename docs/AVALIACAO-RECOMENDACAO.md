# Avaliação do recomendador do Openfy

Data: 2026-10-28. Base: desktop 0.2.9 e Android 0.4.2.

## Resposta direta

- **É comparável ao Spotify?** Não, se "algoritmo" significa o modelo que decide
  o que recomendar. O Openfy tem uma heurística local de reordenação; o Spotify
  tem um conjunto de modelos aprendidos com dados de centenas de milhões de
  ouvintes.
- **É melhor?** Não no geral. É melhor em transparência, privacidade e ausência
  de viés comercial. Isso são qualidades de produto, não de qualidade de
  recomendação.
- **É pior?** Sim, principalmente em descoberta, cold start, uso do histórico
  entre dispositivos, feedback negativo e avaliação.
- **A rádio pode empatar?** Em alguns casos, sim — mas o mérito é do YouTube
  Music. O Openfy só reordena os candidatos que o `next` do YTM devolve. A
  qualidade da rádio é limitada pelo recall do YTM, não pelo modelo local.

Nota editorial, não benchmark: **Openfy ~5,0/10; Spotify ~8,5/10**. A diferença
não é de acabamento; é de categoria.

## Como o Openfy recomenda hoje

### Desktop (0.2.9)

O cérebro fica em `src/main/kotlin/com/openfy/core/Recommender.kt`.

**Sinais usados**

- `play_event` local: faixa, data, `played_ms`, `duration_ms`, `completed`,
  `skipped`, origem e contexto (`Db.playEventsSince`, `Db.kt`).
- Curtidas locais (`liked`).
- Fila e histórico recente da sessão.

Não entram: histórico do celular, playlists, artistas seguidos, recursos de
áudio, gênero, humor, horário, dispositivo, localização, tendências globais ou
qualquer sinal de outros usuários.

**Estatística por faixa** (`Recommender.stats`, janela de 180 dias, até 20 mil
eventos):

```text
completion = média(played_ms / duration_ms, limitado a 0..1)
skipRate   = eventos com skipped / execuções
score      = 0,30 * curtida
           + 0,25 * exp(-diasDesdeÚltima / 45)
           + 0,20 * completion
           + 0,15 * (1 - skipRate)
           + 0,10 * min(1, ln(1 + execuções) / ln(21))
```

Ou seja: curtida vale muito; recência decai com meia-vida de ~31 dias;
conclusão e skip pesam 35% juntos; frequência satura em ~20 execuções.

**Rádio** (`rankRadio`): o YTM devolve uma página de candidatos; o Openfy
reordena com:

```text
score = 0,50 * afinidade       (0,45 se a faixa é desconhecida)
      + 0,20 * mesmoArtista    (1,0 para o artista-semente; 0,35 para os outros)
      + 0,15 * novidade        (1,0 desconhecida; 0,65 se >30 dias; 0,20 recente)
      + 0,10 * rankYTM         (1 / (1 + índice / 10))
      + 0,05 * aleatório
```

O rank original do YTM vale só 10%. Para o artista-semente, o bônus efetivo é
0,20; para os demais, 0,07. Em bibliotecas pequenas, a maior parte dos
candidatos recebe a mesma afinidade (0,45), então quem decide é novidade +
rank + aleatório.

**Diversidade** (`selectDiverse`): janela de 6 artistas, no máximo 2 faixas por
artista e 4 por álbum, e evita faixas ouvidas nas últimas 6 horas. São três
passadas: a primeira respeita tudo; a segunda relaxa a janela de artistas; a
terceira relaxa também os limites. A deduplicação usa título + artista
normalizados (`canonicalKey`), removendo acentos, parênteses, `feat` e sufixos
como `live`/`remaster`.

**Seu mix** (`AppController.startPersonalMix`): monta até 4 sementes com
`topPlayed(5, 180d) + curtidas(5) + recentes(8)`, busca uma rádio do YTM para
cada uma em paralelo, reordena cada página e intercala as listas em rodízio
(`interleave`), limitando a 60 faixas. A continuação de uma das rádios alimenta a
fila. Não há cache: o mix é recalculado a cada clique, com jitter aleatório.

**Rádio automática**: ao terminar uma playlist/álbum, o player emenda a rádio da
última faixa; cada página nova do YTM passa pelo mesmo `rankRadio`.

### Android (0.4.2)

O Android **não tem `Recommender`**. A rádio é a ordem crua do YTM:

- `AuthViewModel.startRadio` pega `page.tracks.take(60)`;
- `appendRadio` só filtra IDs repetidos, chave canônica e artistas das últimas
  4 faixas, e pega 20;
- não há afinidade, conclusão, skip, recência, mix multi-semente nem cache de
  estatísticas.

Isso tem um lado positivo: preserva a ordem do YTM, que é um recomendador
global forte. O lado negativo é que não existe personalização local alguma no
celular, e o "Seu mix" é uma feature só do desktop.

### O que o Android manda para a nuvem

Cada escuta é enviada na hora para `play_events` (`finishEvent` +
`SupabaseApi.pushPlayEvent`). Se o aparelho estiver offline, o evento é perdido;
escutas com menos de 1 segundo são ignoradas. No desktop, os eventos ficam no
SQLite e sobem no próximo sync (`SyncEngine`), mas **não descem**: o
`SyncEngine` só faz push de `play_events`. O recomendador desktop, portanto,
não enxerga o que você ouviu no celular. O Recap enxerga, porque lê as views
remotas; o recomendador não.

## Como o Spotify recomenda (o que é público)

O Spotify descreve publicamente quatro blocos:

1. **Perfil de preferências**: busca, escuta, skip e itens salvos alimentam o
   que ele chama de "taste profile".
2. **Informações do usuário**: idade, artistas seguidos, dispositivo, idioma e
   localização (se autorizada).
3. **Tendências**: comportamento agregado de outros ouvintes; se muita gente
   interage com uma faixa, isso ajuda a recomendá-la.
4. **Conteúdo**: gênero, data de lançamento, sinais de conteúdo gerado por IA e
   metadados; há curadoria editorial combinada com algoritmos ("algotorial").

Além disso, o Spotify já documentou publicamente:

- filtragem colaborativa e fatoração de matrizes para feedback implícito
  (a base histórica do Discover Weekly);
- modelos de áudio e relações entre faixas ("o que costuma ser ouvido junto");
- embeddings de usuário e, em 2026, recomendação generativa com LLM em produção
  para podcasts e em expansão para outras superfícies;
- experimentação em escala (A/B tests, métricas online) e controles como
  "excluir do perfil de preferências", "ocultar/adiar por 30 dias", Smart
  Shuffle e desligar o Autoplay.

O ponto central: o Spotify aprende com **outros usuários** e com **contexto**.
O Openfy aprende apenas com o histórico local de um usuário e com a ordem que o
YTM já produziu.

## Comparação por dimensão

| Dimensão | Openfy | Spotify | Resultado |
|---|---|---|---|
| Fonte de candidatos | Rádio do YouTube Music (`RDAMVM`/`RDEM`) | Catálogo + CF + embeddings + editorial | Pior no controle; comparável em espécie porque o YTM também é global |
| Sinais | Plays, conclusão, skip, curtida, recência | Plays, conclusão, skip, salvos, busca, follows, dispositivo, idioma, localização, tendências | Pior |
| Modelo | Heurística linear fixa | Modelos aprendidos, embeddings, LLM generativo | Muito pior |
| Rádio a partir de faixa/artista | Reordena candidatos do YTM | Ranking próprio + CF + áudio | Um pouco pior; às vezes equivalente |
| Mix pessoal | 4 sementes + rodízio de rádios | Daily Mix, mixes por humor/contexto | Pior |
| Descoberta | Não há Discover Weekly/Release Radar; mistura conhecidos e adjacentes | Discover Weekly, Release Radar, DJ, recomendações editoriais | Muito pior |
| Cold start | Exige histórico/curtidas; sem onboarding | Onboarding, tendências, contexto | Muito pior |
| Repetição | Evita 6h + dedupe canônico | Smart Shuffle, "menos repetições", ocultar/adiar | Comparável em casos simples; pior em controle |
| Diversidade | Caps de artista/álbum + janela | Modelos de diversidade + sinais de sessão | Comparável no básico; pior em escala |
| Feedback negativo | Skip penaliza; não há "não tocar" | Ocultar/adiar 30 dias, excluir do perfil | Pior |
| Contexto | Nenhum | Horário, dispositivo, atividade, sessão | Muito pior |
| Cross-device | Modelo local no desktop; Android sem modelo; eventos não descem | Conta global | Muito pior |
| Explicabilidade | Pesos visíveis no código; decisão auditável | Modelo opaco (com "taste profile" beta) | Melhor |
| Privacidade | Modelo local; histórico sobe ao Supabase só para sync/recap, sem treino global | Coleta global, dispositivo e localização | Melhor |
| Viés comercial | Nenhum | Discovery Mode pode influenciar | Melhor |
| Avaliação | Smoke tests de invariantes; sem métricas | Experimentos online e offline | Muito pior |
| Custo/latência | Local e barato, mas lê até 20 mil eventos por chamada | Infra pesada | Melhor no custo; pior na qualidade |

### Notas editoriais

| Experiência | Openfy | Spotify | Comentário |
|---|---:|---:|---|
| Rádio de faixa/artista | 6,0 | 8,5 | O YTM faz o trabalho pesado; o re-ranker pode até piorar |
| Mix pessoal | 5,0 | 8,5 | Funciona com histórico, mas é um rodízio de rádios |
| Descoberta | 2,5 | 9,0 | Não há superfície dedicada a música nova |
| Cold start | 2,0 | 8,0 | Sem histórico, não há mix |
| Cross-device | 1,5 | 9,0 | O celular ouve, o desktop não aprende |
| Controle/transparência | 8,0 | 6,5 | Código e pesos auditáveis |
| Privacidade/viés | 8,5 | 6,0 | Sem treino global nem Discovery Mode |
| **Geral** | **5,0** | **8,5** | Nota editorial; não é benchmark |

## Onde o Openfy pode ser melhor

1. **Transparência**: a fórmula cabe em uma tela e o código é auditável. O
   Spotify é opaco; mesmo o "taste profile" beta é um resumo, não a regra.
2. **Privacidade**: o modelo roda local e não aprende com outros usuários. O
   custo é justamente a falta de sinal colaborativo. O histórico ainda sobe para
   o Supabase para o Recap, então "local" vale para o modelo, não para todos os
   dados.
3. **Sem viés comercial**: não há Discovery Mode, faixa patrocinada ou
   prioridade paga. O Spotify declara que aspectos comerciais podem influenciar
   recomendações.
4. **Custo e latência**: não há inferência em nuvem; a conta é local. Hoje isso
   é usado de forma ineficiente (20 mil eventos por chamada), mas o teto de
   custo é baixo.
5. **Controle do desenvolvedor/usuário avançado**: pesos, janelas e regras são
   explícitos; dá para ajustar sem treinar um modelo.

Nenhum desses pontos torna a recomendação **mais precisa**. Eles tornam o
produto mais previsível, privado e honesto — o que tem valor, mas é outra
pergunta.

## Onde o Openfy é pior

### 1. Descoberta de verdade não existe

O "Seu mix" usa como sementes as faixas mais ouvidas, curtidas e recentes. As
rádios do YTM para essas sementes tendem a devolver vizinhos próximos. O bônus
de novidade (0,15) e o jitter (0,05) não substituem uma superfície de
descoberta. Não há equivalentes a Discover Weekly, Release Radar ou DJ; o "Seu
mix" é o parente mais próximo de um Daily Mix, mas é gerado na hora. Para música
nova, o Openfy depende inteiramente do que o YTM resolve mostrar.

### 2. Cold start é inexistente

Sem histórico nem curtidas, `startPersonalMix` responde "Curta ou ouça algo
primeiro". A rádio só nasce depois de uma faixa/artista semente. Não há
onboarding de gêneros/artistas, nem fallback para charts/tendências. O Spotify
usa tendências, contexto e um perfil inicial para recomendar desde o primeiro
dia.

### 3. O histórico não é cross-device

O modelo desktop lê apenas o SQLite local. O Android não tem modelo. Os eventos
do Android sobem para o Supabase, mas não descem para o desktop. Quem ouve
metade no celular e metade no PC tem um recomendador que só conhece metade do
gosto. O Recap já resolve isso lendo as views remotas; o recomendador não.

### 4. O feedback negativo é fraco e ruidoso

- Skip entra como penalidade de 0,15 no score, mas não remove a faixa.
- Não há "não tocar", "menos como isso" ou adiamento de 30 dias.
- Fechar o app (desktop: `Player.shutdown`/`Mpv.Event.Closed`; Android:
  `onCleared`) ou sair da conta marca a faixa atual como `skipped`. Isso
  confunde "abandonei o app" com "não gostei da música".
- Eventos mortos por kill do processo ficam `finished=0` e são ignorados; não
  viram skip nem escuta.
- No Android, escutas offline são perdidas; no desktop, entram no próximo sync.

### 5. A ordem do YTM é quase descartada

O rank original recebe 0,10. Para uma biblioteca com poucos dados, a afinidade
é 0,45 para quase todo mundo, e o resultado pode ser dominado por bônus de
artista, novidade e aleatório. O YTM é um recomendador global maduro; jogar fora
90% da ordem dele sem evidência de que o re-ranker é melhor é um risco. O
Android, que não reordena, pode estar mais próximo do YTM do que o desktop.

### 6. Não há aprendizado nem avaliação

Os pesos são fixos e escolhidos à mão. Não há treino, bandit, exploração
controlada, teste offline ou A/B. O único teste (`recommendationSmoke`) verifica
invariantes: sem IDs repetidos, sem duplicata canônica e diversidade de artista
na janela. Isso não mede se a recomendação é boa. Sem log de impressões e sem
métrica de skip/conclusão por recomendação, não dá para afirmar que o re-ranker
melhora o YTM.

### 7. Metadados e normalização têm bugs

- `artistKey` corta o artista no primeiro `,` e no primeiro `&`. "Simon &
  Garfunkel" vira "simon"; "Earth, Wind & Fire" vira "earth". Isso afeta
  `sameArtist`, os caps de diversidade e a deduplicação. O mesmo acontece no
  Android (`radioArtist`).
- `titleKey` remove qualquer parêntese e corta tudo depois de " - ". "Song -
  Part 2" colide com "Song"; versões ao vivo/remaster colidem com o estúdio.
  Parte disso é intencional (evitar duplicata), mas o corte é agressivo demais.
- Não há ID canônico de artista/faixa do YTM; a identidade é inferida por texto.

### 8. Diversidade é só artista/álbum

Não há diversidade de gênero, humor, época ou energia. Em uma biblioteca
pequena, o `avoidHours = 6` pode esvaziar páginas; as passadas relaxam os caps,
mas não o `avoidIds`. O resultado pode ser uma fila curta ou uma repetição de
artistas que o YTM já tinha diversificado.

### 9. Performance

`Recommender.stats` lê até 20 mil eventos e é chamado a cada página de rádio;
`topPlayed` e o mix chamam de novo. Não há cache. Em históricos grandes, isso
custa CPU e I/O no meio da reprodução. Não muda a qualidade, mas limita quantas
vezes o modelo pode ser recalculado.

### 10. Documentação e código divergiam

O comentário do `startPersonalMix` e o README diziam "até 3 sementes"; o código
usa `take(4)`. Corrigido junto com esta avaliação, mas é o tipo de divergência
que atrapalha avaliar.

## O que faria o Openfy chegar perto

### P0 — sem isso, não dá para comparar

1. **Histórico único entre dispositivos**: puxar `play_events` do Supabase para
   o SQLite do desktop e criar um outbox local no Android para escutas offline.
   Sem isso, o modelo nunca vê o uso real completo.
2. **Levar o `Recommender` para o Android** (ou extrair para um módulo `:shared`
   KMP, como o plano de paridade do mobile prevê) e usá-lo em
   `startRadio`/`appendRadio`. Hoje o celular é o cliente mais usado e não tem
   personalização.
3. **Rank fusion em vez de substituição**: dar 35–45% para a ordem do YTM, ou
   usar reciprocal rank fusion, e só subir uma faixa acima do YTM quando houver
   evidência local forte (curtida, conclusão alta, skip baixo). O re-ranker
   precisa provar que ganha do YTM, não presumir.
4. **Corrigir a normalização**: não cortar `&`; só separar artistas quando o
   padrão for claramente uma lista; preservar "Part 2"; usar IDs de artista do
   YTM quando disponíveis.
5. **Medir**: logar impressões (o que foi oferecido, em que posição, de qual
   semente) e o resultado (tocou, pulou, concluiu). Criar um harness offline com
   holdout temporal.

### P1 — o que mais muda a experiência

6. **Item-item barato**: usar coocorrência nas playlists do próprio usuário
   (e nas públicas que ele abrir) como um CF de um usuário só. "Faixas que
   aparecem juntas" é um sinal que o app já tem e não usa.
7. **Feedback negativo explícito**: "não tocar nesta rádio", "menos como isso"
   por 30 dias, e um botão de ocultar. Ajustar o skip para distinguir
   `abandonou`, `pulou` e `falhou`.
8. **Mixes diários cacheados**: 3–4 mixes com temas (familiar, descoberta,
   tranquilo/energético, nostalgia), estáveis no dia e recalculados de madrugada.
9. **Descobertas da semana**: faixas não ouvidas de artistas familiares +
   artistas adjacentes, com limite de novidade e um "por que estou vendo isso".
10. **Cold start**: onboarding de 5 artistas/gêneros e fallback para charts/
    explorar do YTM quando não há histórico.

### P2 — quando houver base

11. Bandit/exploração controlada, embeddings de áudio, pesos aprendidos.
12. Release Radar de artistas seguidos (depende de "seguir artista", que ainda
    não existe).
13. CF real com dados agregados, se o Openfy tiver base de usuários e
    consentimento. Sem base, não há milagre: o teto é o YTM + heurística.

## Como avaliar (e parar de achar)

O erro atual é comparar por impressão. Um protocolo mínimo:

- **Split temporal**: treinar/calibrar com eventos até T; testar com T+2 semanas.
- **Baseline obrigatório**: rádio crua do YTM (como o Android faz hoje) contra
  rádio reordenada. Se o re-ranker não bate o baseline em skip/conclusão, ele
  não deveria estar ligado por padrão.
- **Métricas**:
  - precisão@20 e recall@50 (faixas que o usuário realmente ouviu);
  - skip rate e completion rate das faixas recomendadas;
  - diversidade de artista e álbum;
  - novidade (share de faixas não ouvidas em 30 dias);
  - repetição (mesma faixa/artista em 6h/24h);
  - cobertura (quantos artistas diferentes o sistema consegue servir);
  - cold start (usuários com menos de 20 eventos).
- **Online**: com um usuário só não há A/B estatisticamente útil; comparar
  semanas pareadas e olhar skip/conclusão por contexto. O log de impressões é
  pré-requisito.
- **Critério de aceite**: o re-ranker só vira padrão se melhorar skip ou
  conclusão sem derrubar diversidade e novidade. Caso contrário, preserve a
  ordem do YTM e use o modelo local só como desempate.

## Conclusão

O Openfy não tem um recomendador comparável ao do Spotify. Tem um **re-ranker
local, explicável e barato** em cima do recomendador do YouTube Music. Para
"continuar tocando algo parecido" e montar um mix com o que já se ouve, isso é
suficiente e às vezes agradável. Para descobrir música nova, entender o gosto ao
longo do tempo, funcionar no primeiro uso e aprender com o que é ouvido no
celular, está muito atrás.

Se a pergunta for "o Openfy é melhor que o Spotify?", a resposta honesta é:
**não em recomendação; sim em transparência, privacidade e ausência de viés
comercial**. Se for "dá para chegar perto?", a resposta é: **dá para chegar a um
nível decente com histórico unificado, rank fusion, feedback negativo e
avaliação offline — mas não sem medir, e não sem abrir mão de parte da
simplicidade atual.**

## Referências

- Spotify, "Entenda as recomendações no Spotify", atualizado em 2026-10-23:
  https://www.spotify.com/safetyandprivacy/understanding-recommendations
- Spotify Engineering, "Humans + Machines: A Look Behind the Playlists Powered
  by Spotify's Algotorial Technology" (2023):
  https://engineering.atspotify.com/2023/04/humans-machines-a-look-behind-spotifys-algotorial-playlists
- Spotify Engineering/NVIDIA, "Advancing Generative Recommendation Inference"
  (2026):
  https://engineering.atspotify.com/2026/10/advancing-generative-recommendation-inference-our-collaboration-with-nvidia-on-tensorrt-llm
- Spotify Research, "Search & Recommendations":
  https://research.atspotify.com/search-recommendations
- Código: `src/main/kotlin/com/openfy/core/Recommender.kt`,
  `src/main/kotlin/com/openfy/core/AppController.kt`,
  `src/main/kotlin/com/openfy/player/Player.kt`,
  `src/main/kotlin/com/openfy/core/SyncEngine.kt`,
  `androidApp/app/src/main/kotlin/com/openfy/android/ui/AuthViewModel.kt`.
