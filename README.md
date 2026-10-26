# Openfy — GitHub Pages

Estas páginas são estáticas. Elas servem para:

- landing/download;
- convite de playlist (`join.html?code=...`);
- callback OAuth com PKCE (`oauth/spotify.html`, `oauth/google.html`);
- privacidade/termos.

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

O esquema `openfy://` não precisa de domínio e é o que abre o app instalado.

## Segurança

Não coloque Client Secret, `service_role` do Supabase, tokens ou senhas neste site. O fluxo é PKCE: o `code_verifier` fica no app, a página só repassa `code` e `state` para `openfy://`.
