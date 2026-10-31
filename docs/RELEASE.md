# RELEASE — checklist do desktop

Versão atual: **0.4.0** (mesmo número em `BuildInfo.VERSION`, `version` e
`packageVersion` no `build.gradle.kts`). O verificador de atualizações do app
(`UpdateChecker`) consulta primeiro o manifesto
`https://leo-bsb.github.io/openfy/update-desktop.json`; se ele falhar, cai nas
releases do GitHub comparando com a maior tag `vX.Y.Z` estável. Tags com sufixo
(`-android`) e prereleases/drafts são ignoradas — a release do desktop precisa
usar `vX.Y.Z` puro.

Sem code signing: não temos certificado, então o instalador é distribuído cru
e o Windows pode exibir aviso do SmartScreen. O hash publicado é a prova de
integridade do download.

## 1. Antes de empacotar

- [ ] Atualizar `BuildInfo.VERSION`, `version` e `packageVersion` (os três juntos).
- [ ] Rodar o build de validação e os smokes que a mudança tocar:
  ```powershell
  .\gradlew.bat --no-daemon compileKotlin
  ```
- [ ] Commit de versão separado (ex.: `Desktop 0.2.9`), para a tag apontar
  exatamente para o código empacotado.

## 2. Build do instalador

```powershell
.\gradlew.bat package          # gera .exe + .msi em build/compose/binaries/main/{exe,msi}/
```

- [ ] Testar o `.exe` instalado numa máquina limpa (instala per-user, dados
  ficam em `%LOCALAPPDATA%\OpenfyData`, mpv em `resources-dist/windows/`).

## 3. Hashes

```powershell
Get-FileHash build\compose\binaries\main\exe\Openfy-*.exe -Algorithm SHA256
Get-FileHash build\compose\binaries\main\msi\Openfy-*.msi -Algorithm SHA256
```

- [ ] Colar os SHA-256 na descrição da release (o usuário confere depois de
  baixar, já que não há assinatura).

## 4. Tag + GitHub Release

- [ ] `git tag v0.2.9` e `git push origin v0.2.9` (a partir do commit de versão).
- [ ] Criar a release pela UI da API:
  `https://api.github.com/repos/Leo-bsb/openfy/releases` é lida pelo app —
  tag **sem** sufixo, `draft=false`, `prerelease=false`.
- [ ] Anexar o `.exe` (nome terminado em `.exe`: é o asset que o botão
  "Baixar instalador" do app escolhe) e o `.msi`.
- [ ] Corpo da release: resumo curto + hashes.

## 5. Site e docs

- [ ] Atualizar `site/` com a nova versão e link do instalador.
- [ ] **Publicar o manifesto `site/update-desktop.json`** (ordem importa: binário
  **antes** do JSON):
  ```json
  {
    "version": "0.4.0",
    "url": "https://github.com/Leo-bsb/openfy/releases/download/v0.4.0/Openfy-0.4.0.exe",
    "size": 175373312,
    "sha256": "3e3283…cbb0",
    "notes": "resumo curto em uma linha"
  }
  ```
  `size` tem que ser o tamanho exato (o app cancela o download se faltar byte) e
  `sha256` é conferido antes de abrir o instalador. Se a URL ainda não existir, o
  app recebe 404 e cai no fallback do GitHub — o manifesto fica mentindo por
  alguns minutos.
- [ ] Commitar o manifesto na branch `gh-pages` (worktree `openfy-pages`).
- [ ] Atualizar a tabela de releases no `README.md`
  (ex.: `Desktop 0.4.0: https://github.com/Leo-bsb/openfy/releases/tag/v0.4.0`).
- [ ] Anotar a versão em `docs/` se houver changelog da sprint.
- [ ] Android: repetir o mesmo para `site/update-android.json` e o APK em
  `site/downloads/`.

## 6. Conferência final

- [ ] Abrir o app na versão anterior → Ajustes → Atualizações → "Verificar
  agora" deve apontar a nova release e os dois botões funcionam.
- [ ] Rollback: se o instalador estiver quebrado, apagar/editar a release para
  draft (o app passa a ignorá-la) e re-publicar a anterior por cima.
