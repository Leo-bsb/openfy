# Openfy Android

App Android do Openfy: **conta, sync, busca e player**.

## O que já funciona

- Login/cadastro por e-mail e senha no Supabase Auth.
- Sessão persistida com refresh token.
- Registro do aparelho na tabela `devices`.
- Leitura e criação de playlists na conta.
- Busca de músicas no YouTube Music via InnerTubeX.
- Player com Media3/ExoPlayer: tocar, pausar, seek, anterior/próxima.
- Tocar uma playlist sincronizada do PC.
- Mini player + tela cheia do player.
- Eventos de reprodução enviados ao Supabase (`play_events`) para o recap.
- Tema escuro e logo do Openfy.

Ainda faltam: reprodução em background com `MediaSessionService`, notificação de mídia,
crossfade/EQ/DJ no Android, downloads/offline e biblioteca local.

## Requisitos

- JDK 21
- Android SDK 37 em `C:\Users\leona\AppData\Local\Android\Sdk`
- `androidApp/local.properties` com `sdk.dir`
- `local.properties` na raiz do projeto com as chaves do Supabase

## Build

```powershell
$env:JAVA_HOME="D:\scripts\openfy\.tools\jdk21"
$env:ANDROID_HOME="C:\Users\leona\AppData\Local\Android\Sdk"
.\gradlew.bat assembleRelease
```

APK assinado:

`app\build\outputs\apk\release\app-release.apk`

## Assinatura

- Keystore: `%LOCALAPPDATA%\OpenfyData\keys\openfy-release.jks`
- Senhas/alias: `androidApp/keystore.properties` (ignorado pelo Git)
- **Guarde os dois.** Sem a keystore, não é possível atualizar o APK por cima.

## Instalar no celular

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```

Se estiver vindo de um APK debug, é preciso desinstalar antes (assinaturas diferentes):

```powershell
adb uninstall com.openfy.android
adb install app\build\outputs\apk\release\app-release.apk
```

## Notas técnicas

- AGP 9.3.1 / Gradle 9.6.1 / Kotlin 2.4.20.
- `compileSdk 37`, `targetSdk 37`, `minSdk 26`.
- InnerTubeX Android `v0.11.0` + Media3 `1.8.0`.
- O cliente HTTP de conta/sync usa Ktor CIO.
- As chaves do Supabase entram via `BuildConfig` gerado do `local.properties` da raiz e **não** são versionadas.
- A sessão ainda usa SharedPreferences privadas; antes de distribuir publicamente, migrar para armazenamento criptografado.
