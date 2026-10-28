# RELEASE — checklist do desktop

Versão atual: **0.2.8** (mesmo número em `BuildInfo.VERSION`, `version` e
`packageVersion` no `build.gradle.kts`). O verificador de atualizações do app
(`UpdateChecker`) compara com a maior tag `vX.Y.Z` estável publicada aqui;
tags com sufixo (`-android`) e prereleases/drafts são ignoradas.

Sem code signing: não temos certificado, então o instalador é distribuído cru
e o Windows pode exibir aviso do SmartScreen. O hash publicado é a prova de
integridade do download.

## 1. Antes de empacotar

- [ ] Atualizar `BuildInfo.VERSION`, `version` e `packageVersion` (os três juntos).
- [ ] Rodar o build de validação e os smokes que a mudança tocar:
  ```powershell
  .\gradlew.bat --no-daemon compileKotlin
  ```
- [ ] Commit de versão separado (ex.: `Desktop 0.2.8`), para a tag apontar
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

- [ ] `git tag v0.2.8` e `git push origin v0.2.8` (a partir do commit de versão).
- [ ] Criar a release pela UI da API:
  `https://api.github.com/repos/Leo-bsb/openfy/releases` é lida pelo app —
  tag **sem** sufixo, `draft=false`, `prerelease=false`.
- [ ] Anexar o `.exe` (nome terminado em `.exe`: é o asset que o botão
  "Baixar instalador" do app escolhe) e o `.msi`.
- [ ] Corpo da release: resumo curto + hashes.

## 5. Site e docs

- [ ] Atualizar `site/` com a nova versão e link do instalador.
- [ ] Atualizar a tabela de releases no `README.md`
  (ex.: `Desktop 0.2.8: https://github.com/Leo-bsb/openfy/releases/tag/v0.2.8`).
- [ ] Anotar a versão em `docs/` se houver changelog da sprint.

## 6. Conferência final

- [ ] Abrir o app na versão anterior → Ajustes → Atualizações → "Verificar
  agora" deve apontar a nova release e os dois botões funcionam.
- [ ] Rollback: se o instalador estiver quebrado, apagar/editar a release para
  draft (o app passa a ignorá-la) e re-publicar a anterior por cima.
