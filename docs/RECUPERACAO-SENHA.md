# Recuperação de senha

> **05/11/2026:** o fluxo voltou no desktop 0.7.2 e no Android 0.11.3. O botão
> **Esqueci minha senha** abre um diálogo que envia o link para
> `https://leo-bsb.github.io/openfy/recover.html`, onde o usuário define a nova
> senha. O caminho por código continua no cliente, mas só funciona com SMTP
> próprio: no plano gratuito com o provedor padrão o Supabase não permite editar
> o template para incluir `{{ .Token }}`.

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

- No plano gratuito com o provedor de e-mail padrão, o Supabase **bloqueia a
  edição de templates** ("Email template modification is not available for free
  tier projects using the default email provider"). Nesse caso o e-mail traz
  apenas `{{ .ConfirmationURL }}`, e o fluxo do app é o link — não há campo de
  código no diálogo.
- Para habilitar o caminho por código, configure um SMTP próprio e inclua
  `{{ .ConfirmationURL }}` **e** `{{ .Token }}` no template "Reset Password".
  O código expira; se o usuário demorar, use "Esqueci minha senha" de novo.

## Ordem de teste

1. Ajustes → Conta → Esqueci minha senha → enviar para o próprio e-mail.
2. Abrir o link no mesmo navegador: a página pede a nova senha e confirma.
3. Entrar com a senha nova no desktop e no Android.

O endpoint é público e responde `200 {}` mesmo para e-mail inexistente (não
vaza quem tem conta). Para conferir a chave sem enviar e-mail, use
`POST /auth/v1/recover` com um endereço `@example.invalid`.
