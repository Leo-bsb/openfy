# Openfy — configuração inicial para online + Android

Este é o mínimo que só você pode fazer (criar contas, escolher senhas e instalar SDKs).
O resto — schema, RLS, sync, app Android, assinatura do APK — eu preparo no projeto.

---

## 1. Supabase (obrigatório para sync/colaboração/social)

### 1.1 Criar o projeto

1. Crie uma conta em https://supabase.com.
2. **New project**:
   - Nome: `openfy` (ou outro).
   - Região: a mais próxima de você (ex.: `South America (São Paulo)` se disponível).
   - Guarde a **senha do banco** no seu gerenciador de senhas. O app não usa essa senha; ela é para administração.
3. Espere o projeto terminar de provisionar.

### 1.2 Copiar as chaves do app

Em **Project Settings → API**:

- Copie **Project URL** (ex.: `https://abcdefgh.supabase.co`).
- Copie a chave **anon public** (a chave pública; pode ficar no app).
- **Nunca** copie/cole a `service_role` no app, no chat ou no repositório. Ela ignora RLS e dá acesso total.

Salve no arquivo local do projeto:

`D:\scripts\openfy\local.properties`

```properties
openfy.supabase.url=https://SEU-PROJETO.supabase.co
openfy.supabase.anonKey=SUA_ANON_KEY
```

Esse arquivo não deve ser commitado. Se preferir, use as variáveis de ambiente:

```powershell
$env:OPENFY_SUPABASE_URL="https://SEU-PROJETO.supabase.co"
$env:OPENFY_SUPABASE_ANON_KEY="SUA_ANON_KEY"
```

> Não precisa colar as chaves aqui no chat. Salve no arquivo e diga apenas “Supabase pronto”.

### 1.3 Ativar login por e-mail

Em **Authentication → Providers → Email**:

- Habilite **Email**.
- Para desenvolvimento rápido, desabilite **Confirm email** (o usuário entra na hora).
- Para uso real, mantenha confirmação ligada e configure SMTP próprio em **Authentication → SMTP Settings** (Resend, Brevo, SendGrid etc.). O SMTP padrão do Supabase tem limite baixo de e-mails.

> **Status do projeto atual:** o login por e-mail está ativo e a confirmação foi destravada. O projeto Supabase já está configurado em `local.properties`.

Em **Authentication → URL Configuration**:

- Site URL: `http://localhost`
- Redirect URLs (quando formos fazer deep link): `openfy://auth-callback`
- No Android, o app usará e-mail/senha no v1; deep link de recuperação de senha pode entrar depois.

### 1.4 Storage de capas

Não precisa criar manualmente: a migration do projeto cria o bucket `covers` e as políticas. Se você quiser criar antes:

- **Storage → New bucket** → `covers` → **Private**.
- O app baixa via URL assinada ou política autenticada.

### 1.5 O que eu faço depois que você salvar as chaves

- Migrations SQL (tabelas, índices, `server_rev`, triggers, RLS).
- Buckets/políticas de capa.
- RPCs de sync e colaboração.
- Testes de duas instâncias (PC como dois “aparelhos”) contra o seu projeto.
- Nada de `service_role` no cliente.

### 1.6 Rodar o schema inicial

O SQL completo está em:

`D:\scripts\openfy\supabase\migrations\0001_initial.sql`

Para copiar tudo para a área de transferência:

```powershell
Get-Content "D:\scripts\openfy\supabase\migrations\0001_initial.sql" -Raw | Set-Clipboard
```

No Supabase: **SQL Editor → New query → colar → Run**.

O script é idempotente na maior parte (usa `if not exists`, `create or replace`, `drop policy if exists`) e cria:

- perfis/dispositivos;
- tracks, playlists, membros, itens, curtidas;
- eventos de reprodução, playback state;
- follows/activities (social v1);
- RLS, RPCs de convite, capas no Storage e views de recap.

Se aparecer erro, mande a mensagem exata; eu ajusto o SQL.

---

## 2. Android (obrigatório para o APK)

### 2.1 Ferramentas

Instale:

- **Android Studio** (versão estável atual).
- **Android SDK Platform 36** (ou a estável mais recente; o projeto vai mirar 36).
- **Android SDK Build-Tools** e **Platform-Tools**.
- **JDK 21** — o projeto já tem um em `D:\scripts\openfy\.tools\jdk21`.

No Android Studio: **SDK Manager** → marque `Android SDK Platform 36`, `Android SDK Build-Tools`, `Android SDK Platform-Tools`, `Android Emulator` (opcional).

### 2.2 Aparelho de teste

- Um celular Android **8.0+** (recomendado 10+) com **Depuração USB** ligada.
- Ou um emulador com Google APIs para a primeira tela; para testar background, Bluetooth e áudio, use aparelho físico.

### 2.3 Keystore de assinatura (importante)

O APK de release precisa de uma chave. Se perder a chave, não consegue instalar atualizações por cima da versão antiga.

- Escolha uma senha forte e guarde no gerenciador de senhas.
- Podemos gerar a keystore no projeto depois, em uma pasta fora do repositório (ex.: `%LOCALAPPDATA%\OpenfyData\keys\openfy-release.jks`).
- Não use a mesma senha do Supabase.

### 2.4 Push/FCM (opcional no v1)

Se quisermos notificações de atividade social com o app fechado:

- Criar projeto no Firebase.
- Adicionar app Android `com.openfy.android`.
- Baixar `google-services.json`.

Sem isso, o v1 funciona com feed/sync ao abrir o app; notificações em background ficam para depois.

---

## 3. Distribuição do APK

- **Sideload**: geramos um APK assinado e você instala via USB/arquivo.
- Atualizações: GitHub Releases + Obtainium, ou reinstalar o APK por cima (mesma keystore).
- **Play Store não entra no v1** (risco alto por causa da extração não oficial do YouTube). Se um dia for tentar, será outro projeto de conformidade.

---

## 4. GitHub Pages + OAuth (opcional; só para importar playlists)

OAuth **não é necessário** para sync, colaboração, recap ou Android. Ele serve apenas para importar playlists do Spotify/Google.

### 4.1 GitHub Pages serve como domínio?

**Sim, para páginas estáticas.** GitHub Pages dá HTTPS e um endereço público:

```
https://SEU-USUARIO.github.io/openfy/
```

Ele serve para:

- landing page / página de download do APK;
- política de privacidade e termos;
- link de convite de playlist (`join.html?code=...`) que redireciona para `openfy://join/...`;
- callback OAuth estático com PKCE (`oauth/spotify.html`, `oauth/google.html`);
- arquivos `.openfyplaylist` para download, se quiser.

Ele **não serve** como backend: não roda código com segredo, não hospeda banco, não faz Realtime. Para isso continua sendo o Supabase.

Limitações:

- O callback OAuth precisa ser **HTTPS** e exatamente igual ao cadastrado no provedor.
- Páginas de projeto ficam em `usuario.github.io/openfy/...`; para Android App Links “de verdade” (`.well-known/assetlinks.json` na raiz) seria preciso um domínio próprio ou um repositório `usuario.github.io`. No v1, use o esquema `openfy://`, que não precisa de domínio.
- Se usar domínio próprio, adicione um `CNAME` no repositório e configure o DNS; aí pode ter `https://openfy.seudominio.com/...`.

### 4.2 Spotify — o que pegar

1. Acesse https://developer.spotify.com/dashboard e crie um app.
2. Em **Redirect URIs**, cadastre **exatamente** um destes:
   - Desktop: `http://127.0.0.1:8899/callback` (loopback IPv4 é permitido; `localhost` não é mais aceito).
   - GitHub Pages: `https://leo-bsb.github.io/openfy/oauth/spotify.html`.
   - Você pode cadastrar os dois.
3. Marque **Web API** e os escopos de leitura:
   - `playlist-read-private`
   - `playlist-read-collaborative`
   - `user-library-read`
4. Copie o **Client ID**. Para desktop/Android usaremos **Authorization Code + PKCE**, sem Client Secret.
5. Em **User Management**, adicione sua conta Spotify enquanto o app estiver em **Development Mode**. Sem isso, a API responde 403 para a sua conta.
6. Salve em `local.properties`:
   ```properties
   openfy.spotify.clientId=SEU_CLIENT_ID
   openfy.spotify.redirectUri=http://127.0.0.1:8899/callback
   ```

Limite: app em Development Mode aceita até 25 usuários. Para distribuir publicamente, o Spotify exige solicitação de extensão de quota.

### 4.3 Google / YouTube — o que pegar

1. Crie um projeto no Google Cloud Console.
2. Ative a **YouTube Data API v3**.
3. Configure **OAuth consent screen**:
   - External (para uso pessoal, pode ficar em Testing);
   - adicione seu e-mail como **test user**;
   - escopo `https://www.googleapis.com/auth/youtube.readonly`.
4. Crie **Credentials → OAuth client ID**:
   - **Desktop app**: redirect `http://127.0.0.1:8899/callback`; copie Client ID e Client Secret (em apps instalados o secret não é confidencial, mas não o publique).
   - **Android**: package `com.openfy.android` + SHA-1 da keystore; usado com Google Sign-In.
   - **Web application** (se usar GitHub Pages): redirect `https://SEU-USUARIO.github.io/openfy/oauth/google.html`.
5. Salve:
   ```properties
   openfy.google.clientId=SEU_CLIENT_ID
   openfy.google.clientSecret=SEU_CLIENT_SECRET
   openfy.google.redirectUri=http://127.0.0.1:8899/callback
   ```

Cuidados:

- A YouTube Data API serve para **metadados/importação**, não para tocar áudio. O playback continua via InnerTube.
- Quota padrão: 10.000 unidades/dia; importar playlists consome pouco, mas listas enormes gastam chamadas.
- O escopo `youtube.readonly` é sensível: para distribuir publicamente, o Google pode exigir verificação do app. Para uso pessoal, Testing + test users resolve.
- Se você usar um client “Web” no GitHub Pages, o ideal é trocar o código no servidor (Supabase Edge Function) para não expor o secret. Para o v1, prefira loopback no desktop e Google Sign-In nativo no Android.

### 4.4 Como usar o GitHub Pages no fluxo

O repositório já existe: `https://github.com/Leo-bsb/openfy`.
As páginas prontas estão em `D:\scripts\openfy\site\`.

1. Crie uma branch `gh-pages` (ou use a pasta `/docs` da branch principal).
2. Copie o conteúdo de `site/` para a raiz da branch `gh-pages`.
3. Em **Settings → Pages**, publique a branch `gh-pages` na raiz.
4. A URL será `https://leo-bsb.github.io/openfy/`.
5. Cadastre as URLs exatas nos provedores.

### 4.5 O que **não** colocar no GitHub Pages

- Client Secret do Spotify/Google em JavaScript;
- `service_role` do Supabase;
- senhas, tokens de acesso ou refresh tokens.

Se um dia quisermos trocar código/token no servidor, usamos uma **Edge Function** do Supabase com os secrets guardados em `supabase secrets`.

---

## 5. O que dá para começar sem as chaves

Eu posso adiantar, sem bloquear:

- Refactor do core compartilhado (KMP).
- Schema local v4 (outbox/cursor/`server_rev`).
- Migração de `playlist_item` para item único + rank.
- Instrumentação de `play_event` (tempo ouvido, skip, contexto).
- Estrutura do app Android (Gradle, Compose, Media3) compilando.
- SQL/migrations do Supabase e telas de login/sync com placeholders.
- Testes headless de conflito usando dois bancos locais.

O que **só funciona depois** das chaves/projeto:

- Login real, push/pull contra a nuvem.
- Colaboração/realtime.
- Sync PC ↔ celular.
- Social/feed público.
- Recap consolidado entre aparelhos.

---

## 6. Checklist mínimo para eu começar a integração

- [x] Projeto Supabase criado e SQL aplicado.
- [x] `local.properties` com `openfy.supabase.url` e `openfy.supabase.anonKey`.
- [x] Login por e-mail habilitado (auto-confirm).
- [x] Android Studio + SDK 36 + platform-tools + build-tools instalados.
- [ ] Aparelho/emulador Android para testar.
- [x] Repositório GitHub + GitHub Pages publicados.
- [ ] Redirect URIs e test user no Spotify.
- [ ] Senha da keystore escolhida (podemos gerar depois).
- [ ] (Opcional) Firebase/FCM.

O repositório está em `https://github.com/Leo-bsb/openfy` e o site em
`https://leo-bsb.github.io/openfy/`. O SDK Android está em
`C:\Users\leona\AppData\Local\Android\Sdk`.

Quando o aparelho/emulador estiver pronto, consigo gerar o primeiro APK de teste.
