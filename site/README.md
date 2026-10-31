# Openfy — GitHub Pages

Estas páginas são estáticas. Elas servem para:

- landing/download;
- convite de playlist (`join.html?code=...`);
- recuperação de senha (`recover.html`, ver `docs/RECUPERACAO-SENHA.md`);
- callback OAuth com PKCE (`oauth/spotify.html`, `oauth/google.html`);
- privacidade/termos;
- manifestos de atualização lidos pelos apps:
  - `update-desktop.json` (desktop: versão, `.exe`, tamanho e SHA-256);
  - `update-android.json` (Android: versão, `.apk`, tamanho e SHA-256).

## Publicar

Opção A — branch `gh-pages` (recomendado para não misturar com o app):

1. Crie uma branch `gh-pages` no repositório `Leo-bsb/openfy`.
2. Copie o conteúdo desta pasta `site/` para a raiz dessa branch.
3. No GitHub: **Settings → Pages → Build and deployment → Deploy from a branch**.
4. Selecione a branch `gh-pages` e a pasta `/ (root)`.
5. A URL será `https://leo-bsb.github.io/openfy/`.

Opção B — pasta `/docs` na branch principal:

1. Copie o conteúdo de `site/` para `docs/` (ou aponte o Pages para `/docs`).
2. Em **Settings → Pages**, escolha a branch principal e a pasta `/docs`.

## URLs para cadastrar no OAuth

- Spotify: `https://leo-bsb.github.io/openfy/oauth/spotify.html`
- Google: `https://leo-bsb.github.io/openfy/oauth/google.html`
- Convite: `https://leo-bsb.github.io/openfy/join.html?code=CODIGO`
- Recuperação de senha: `https://leo-bsb.github.io/openfy/recover.html` (precisa
  estar nas *Redirect URLs* do Supabase, junto com
  `https://leo-bsb.github.io/openfy/**`)

O esquema `openfy://` não precisa de domínio e é o que abre o app instalado.

## Manifestos de atualização

O app lê o JSON direto do Pages, com cache-buster (`?t=<epoch>`) e
`Cache-Control: no-cache`. Publique o manifesto **depois** do binário existir:
um `url` apontando para release/asset inexistente faz o download falhar com
404. O `size` precisa ser o valor exato do arquivo; o `sha256` é opcional no
Android e conferido no desktop quando presente.

```json
{
  "version": "0.4.0",
  "url": "https://github.com/Leo-bsb/openfy/releases/download/v0.4.0/Openfy-0.4.0.exe",
  "size": 175373312,
  "sha256": "b887620c…",
  "notes": "resumo curto em uma linha"
}
```

## Segurança

Não coloque Client Secret, `service_role` do Supabase, tokens ou senhas neste site. O fluxo é PKCE: o `code_verifier` fica no app, a página só repassa `code` e `state` para `openfy://`.
