# mpv.exe

O `mpv.exe` **não é versionado** neste repositório porque tem ~121 MB (acima do limite de 100 MB por arquivo do GitHub).

Para buildar o app/instalador localmente:

1. Baixe o mpv para Windows em https://mpv.io/installation/ (ou use o build já existente em `dist/`).
2. Coloque `mpv.exe` nesta pasta (`resources-dist/windows/mpv.exe`).
3. Mantenha o `d3dcompiler_43.dll` que já está versionado.

Sem o `mpv.exe`, o app compila, mas o player não inicia.
