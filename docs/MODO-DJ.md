# Modo DJ (N1 e N2)

O Modo DJ troca o crossfade linear por uma transição com pulso, compasso e
loudness. É uma preferência **local do aparelho** (SQLite no desktop,
`SharedPreferences` no Android), não entra no `SyncedSettings` e não vai para a
nuvem.

## O que cada nível faz

- **N1 (smart fade)** — funciona com qualquer faixa: encaixa o fade em um número
  inteiro de compassos quando existe grade, ajusta o ganho para a loudness da
  faixa que entra e faz o bass swap (corta os graves de quem sai e tira o corte
  de quem entra na metade do fade).
- **N2 (beatmatch)** — exige as duas grades confiáveis e diferença de tempo de
  até 8% (com ajuste de meio/dobro tempo: 60 BPM casa com 120 BPM). A faixa que
  entra começa no primeiro downbeat, tem o tempo casado e o fade dura um número
  inteiro de compassos, terminando no fim da faixa atual.

O plano é calculado por `DjMixer.plan()` e devolvido como `MixPlan`
(`beatmatched`, `fadeSeconds`, `speed`, `entrySeconds`, `oldGain`, `reason`).
As regras completas estão no KDoc de `src/main/kotlin/com/openfy/core/DjMixer.kt`.

## Como a grade é analisada

`BeatAnalyzer.analyze()` recebe PCM mono (float):

1. `BpmAnalyzer` estima o pulso por envelope de onsets + autocorrelação.
2. O BPM é refinado em ±3,5% (passos de 0,05%) escolhendo o valor que deixa os
   ataques mais nítidos; depois há um refinamento fino de ±0,1%.
3. A fase final usa um envelope de 5,8 ms (`FINE_HOP = 128` a 22,05 kHz). O
   instante da batida compensa a janela de análise: `i * hop + FRAME`.
4. O downbeat (o "1" do compasso de 4 tempos) é escolhido pelo envelope de graves
   (~180 Hz), para o kick mandar mais que pratos e contratempos.
5. `rmsDb` mede a loudness típica para o ganho do mix.

A calibração sintética (90, 120, 128 e 174 BPM) fica em
`src/test/kotlin/com/openfy/core/BeatGridTest.kt` e aceita erro de 0,15 BPM,
fase de 30 ms e downbeat de 50 ms.

## Cache e fallback

- **Desktop:** `BeatGridService` decodifica 0–72 s com o próprio mpv
  (`PcmDecoder`) e guarda a grade no SQLite v10, tabela `beat_grid`.
- **Android:** `BeatGridStore` decodifica os primeiros 60 s com `MediaCodec` e
  guarda em `SharedPreferences` (`openfy_beat_grid`), uma entrada por faixa.
- Cada aparelho analisa cada faixa **uma vez**. Enquanto a grade da próxima não
  estiver pronta, a transição cai no N1.
- Streams remotos no Android não são decodificados: sem arquivo local/baixado, o
  mix fica no N1 (`highpass`/`lowshelf`, ganho e compasso quando houver grade da
  faixa atual).

## Integração com o player

**Desktop (`Player.kt` + `Mpv.kt`)**

- O standby é carregado com `start=<entrySeconds>` e fica **pausado** no downbeat
  até o fade começar; o speed é aplicado com `set_property speed` (mpv mantém o
  tom via `scaletempo2`).
- Bass swap por player com `highpass=f=190:width_type=q:w=0.7` na entrada e
  `lowshelf=f=190:g=-8` na saída; os filtros são anexados à cadeia do EQ.
- O ganho da faixa que sai é `10^((inRms - outRms)/20)` limitado a 0,5–1,25.
- Ao terminar o mix, o `speed` volta a 1.0 em ~6 s.

**Android (`AuthViewModel.kt` + `EqualizerController.kt`)**

- O standby é preparado no `entrySeconds` com `playWhenReady = false` e
  `PlaybackParameters(speed)`; o fade começa com `playWhenReady = true`.
- `EqualizerController.setBassCut()` corta os graves por player (corte total até
  125 Hz, metade até 300 Hz) sem mexer no EQ global.
- No fim do mix o `speed` volta a 1.0 suavemente (~6 s).

## Calibrar e diagnosticar

```powershell
$env:JAVA_HOME="D:\scripts\openfy\.tools\jdk21"
.\gradlew.bat bpmProbe
```

O relatório sai em `build\bpm-report\index.html` e mostra BPM, fase, downbeat e
confiança dos arquivos de `%LOCALAPPDATA%\OpenfyData\downloads`. Para apontar
outra pasta:

```powershell
.\gradlew.bat bpmProbe -Pdir="D:\caminho\com\musicas"
```

A saída esperada na calibração é BPM com erro < 0,05, fase ~0,151 s para ataques
em 0,15 s e downbeat no mesmo instante nos quatro tempos.

## Limites conhecidos

- O compasso é assumido em 4/4; não há editor manual de grade.
- A entrada usa o primeiro downbeat detectado (até 8 s). Em intros sem pulso, é
  o primeiro trecho com batida detectado.
- Não há separação de stems nem mashup; a transição é A/B com EQ, ganho e tempo.
- A confiança é uma heurística (z-score do envelope), não uma medida musical.
- A validação é por build, testes e análise de áudio real; ainda falta um teste
  em aparelho Android e uma escuta comparativa no desktop.
