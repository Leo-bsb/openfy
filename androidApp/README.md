# Openfy Android

Primeiro app Android do Openfy: **login e sync mínimo** com a mesma conta do PC.

## O que já funciona

- Login/cadastro por e-mail e senha no Supabase Auth.
- Sessão persistida localmente (refresh token).
- Registro do aparelho na tabela `devices`.
- Leitura das playlists da conta.
- Criação de playlist (já aparece no PC depois do sync).
- Tema escuro com a paleta do Openfy.

Ainda **não** tem player, busca, downloads ou biblioteca local. Essas peças entram nas próximas etapas.

## Requisitos

- JDK 21
- Android SDK 36 em `C:\Users\leona\AppData\Local\Android\Sdk`
- `androidApp/local.properties` com `sdk.dir`
- `local.properties` na raiz do projeto com as chaves do Supabase

## Build

```powershell
$env:JAVA_HOME="D:\scripts\openfy\.tools\jdk21"
$env:ANDROID_HOME="C:\Users\leona\AppData\Local\Android\Sdk"
.\gradlew.bat assembleDebug
```

APK:

`app\build\outputs\apk\debug\app-debug.apk`

## Instalar no celular

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Ou abra o projeto `androidApp` no Android Studio e rode no aparelho.

## Notas técnicas

- AGP 9.0.1 / Gradle 9.1.0 / Kotlin 2.3.20.
- `compileSdk 36` porque o platform 37 ainda não está disponível no SDK atual.
- O cliente HTTP usa Ktor CIO (o engine OkHttp 5.5 exige compileSdk 37).
- As chaves do Supabase entram via `BuildConfig` gerado do `local.properties` da raiz e **não** são versionadas.
- A sessão ainda usa SharedPreferences privadas; antes de distribuir o APK, migrar para armazenamento criptografado.
