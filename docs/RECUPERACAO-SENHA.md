# Recuperação de senha

O Openfy troca a senha por dois caminhos, ambos com a chave anônima/publishable
— não é preciso `service_role` nem CLI:

1. **Link do e-mail** → `https://leo-bsb.github.io/openfy/recover.html`
   (página estática em `site/recover.html`, publicada no `gh-pages`). Ela lê
   `access_token`/`refresh_token` do hash (ou `token_hash`/`token` com
   `type=recovery`) e chama `PUT /auth/v1/user` com a senha nova.
2. **Código no app** → o usuário cola o código do e-mail; o app chama
   `POST /auth/v1/verify` com `{type:"recovery", token, email}` e depois
   `PUT /auth/v1/user` com o `access_token` recebido.

## O que precisa estar configurado no Supabase

Sem estes dois ajustes o fluxo falha **em silêncio** (o e-mail sai, mas o link
cai na página errada ou não traz código):

### 1. Redirect URLs

*Authentication → URL Configuration*:

- **Site URL**: `https://leo-bsb.github.io/openfy/`
- **Redirect URLs**: incluir
  - `https://leo-bsb.github.io/openfy/recover.html`
  - `https://leo-bsb.github.io/openfy/**` (curinga, cobre `join.html` e o resto)

Se `recover.html` não estiver na lista, o Supabase ignora o `redirect_to` que o
app envia e manda o link para a **Site URL**. A página inicial não trata
`#access_token`, então o usuário vê o site e não consegue trocar a senha.

### 2. Template "Reset Password"

*Authentication → Email Templates → Reset Password*:

- incluir `{{ .ConfirmationURL }}` (o link) **e** `{{ .Token }}` (o código de 6
  dígitos que o app pede). O template padrão manda só o link, e aí o caminho
  "Já tenho o código" fica inutilizável.
- O código expira; se o usuário demorar, use "Esqueci minha senha" de novo.

## Ordem de teste

1. Ajustes → Conta → Esqueci minha senha → enviar para o próprio e-mail.
2. Abrir o link no mesmo navegador: a página pede a nova senha e confirma.
3. Repetir e usar o código no app (campo "Já tenho o código").
4. Entrar com a senha nova no desktop e no Android.

O endpoint é público e responde `200 {}` mesmo para e-mail inexistente (não
vaza quem tem conta). Para conferir a chave sem enviar e-mail, use
`POST /auth/v1/recover` com um endereço `@example.invalid`.
