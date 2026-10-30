# Apoiador, GIFs, peso e escala do banco

Data: 2026-10-30. Base: commit `3a9cad0` + árvore de trabalho desta data. Linhas
conferidas nos arquivos citados; `App.kt`, `OpenfyApp.kt` e `AuthViewModel.kt`
estão em edição por outras frentes — se o número andar, localize pelo símbolo.
Medições marcadas como "medido" (tamanhos por `Get-Item`/ZIP; bytes/evento no
`openfy.db` local); projeções, como "(estimativa)".

## Resumo e decisão

1. **Apoiador é viável sem service_role no cliente e sem CLI**: tabela de códigos
   pré-gerados, `supporters` e RPC `SECURITY DEFINER`; o app lê só o próprio
   direito e guarda cache para offline.
2. **Não venda antes de corrigir o sync.** O pull de playlists, itens, curtidas e
   faixas é completo a cada 120 s nos dois clientes (`SyncEngine.kt:38-56`,
   `AuthViewModel.kt:509-517` e `753-783`): ~1,3 GB/mês por dispositivo casual e
   ~9 GB/mês por pesado (estimativa).
3. **Cosmético primeiro, mídia depois.** Tema e recap exportado não criam custo
   recorrente; GIF cria storage/egress; clipe de 15 s **não é possível com o
   extrator atual**, que só devolve áudio (`YouTube.kt:917-953`).
4. **Preço sugerido: R$ 39,90 uma vez** pelo pacote cosmético abaixo, sem prometer
   recursos futuros nem hospedagem ilimitada (1.5).

## 1. Versão apoiador (doação única)

### 1.1 Modelo de entitlement

Fluxo: compra (Pix/manual ou Stripe Payment Link/Ko-fi) → entrega de **um código
de uso único** → usuário autenticado digita no app → RPC valida e grava o direito
→ app guarda o direito e funciona offline.

Linha pré-gerada vence código HMAC: o uso único é garantido pelo `unique` e pelo
`redeemed_by`, a revogação é simples e não há segredo no app. HMAC emitiria sem
tocar no banco, mas o reuso ainda exigiria tabela de resgate e um código vazado
não seria revogável sem blacklist. A RPC roda como `postgres`, no padrão da 0002
(`0002_security_hardening.sql:61-95`), e o cliente continua só com a chave anon.
A migration nova deve ser **`0004_supporters.sql`**: 0003 já está reservado para a
migration social (`Profile.kt:8`, `ProfilePrefs.kt:9`).

```sql
-- 0004_supporters.sql (proposto; não aplicar sem revisão)
create table if not exists public.supporter_codes (
    id uuid primary key default gen_random_uuid(),
    code_hash text not null unique,
    plan text not null default 'lifetime' check (plan in ('lifetime','annual')),
    batch text not null default '',
    created_at timestamptz not null default now(),
    redeemed_by uuid references public.profiles(id) on delete set null,
    redeemed_at timestamptz
);
create table if not exists public.supporters (
    user_id uuid primary key references public.profiles(id) on delete cascade,
    plan text not null check (plan in ('lifetime','annual')),
    granted_at timestamptz not null default now(),
    expires_at timestamptz,
    revoked_at timestamptz,
    source text not null default 'code'
);
alter table public.supporters enable row level security;
alter table public.supporter_codes enable row level security;
-- Só o dono lê o próprio direito; nunca expor code_hash.
revoke all on public.supporters from anon, authenticated;
grant select (user_id, plan, granted_at, expires_at, revoked_at)
    on public.supporters to authenticated;
revoke all on public.supporter_codes from anon, authenticated;
drop policy if exists supporters_self on public.supporters;
create policy supporters_self on public.supporters
    for select to authenticated using (user_id = auth.uid());

create or replace function public.redeem_supporter_code(p_code text)
returns table (plan text, granted_at timestamptz, expires_at timestamptz)
language plpgsql security definer set search_path = public as $$
declare v_hash text; v_row public.supporter_codes%rowtype;
begin
    if auth.uid() is null then raise exception 'not_authenticated'; end if;
    v_hash := encode(sha256(convert_to(upper(btrim(p_code)), 'UTF8')), 'hex');
    select * into v_row from public.supporter_codes
     where code_hash = v_hash for update;
    if not found then
        perform pg_sleep(0.25);      -- freio simples contra varredura
        raise exception 'invalid_code';
    end if;
    if v_row.redeemed_by is not null then raise exception 'code_already_used'; end if;
    update public.supporter_codes
       set redeemed_by = auth.uid(), redeemed_at = now() where id = v_row.id;
    insert into public.supporters (user_id, plan, granted_at, expires_at, source)
    values (auth.uid(), v_row.plan, now(),
        case when v_row.plan = 'annual' then now() + interval '1 year' end, 'code')
    on conflict (user_id) do update
       set plan = excluded.plan,
           granted_at = least(public.supporters.granted_at, excluded.granted_at),
           expires_at = case when excluded.plan = 'lifetime' then null
                             else greatest(public.supporters.expires_at, excluded.expires_at) end,
           revoked_at = null;
    return query select s.plan, s.granted_at, s.expires_at
        from public.supporters s where s.user_id = auth.uid();
end; $$;
-- create function concede EXECUTE a PUBLIC por padrão; feche como na 0002.
revoke all on function public.redeem_supporter_code(text) from public, anon;
grant execute on function public.redeem_supporter_code(text) to authenticated;
notify pgrst, 'reload schema';
```

`sha256()` nativo exige Postgres 11+; confirme a versão. A 0001 concede CRUD em
todas as tabelas **existentes** a `authenticated` (`0001_initial.sql:903-906`);
isso não atinge tabelas novas, mas **não rode a 0001 de novo** depois de criar
estas, ou o grant geral volta — se rodar, repita os `revoke`. Rate limit real (5
tentativas/hora) pede tabela de tentativas; `pg_sleep` só encarece a varredura.

O lote é gerado no SQL Editor criando uma tabela temporária com 50 valores de 16
caracteres hex, inserindo `sha256()` desses valores e mostrando o texto puro só
uma vez para copiar e descartar (64 bits, `unique` no hash, uso único).

### 1.2 Ativação e pagamento

- Pix manual: comprovante → você responde com 1 código do lote. Sem taxa nem
  servidor, mas trabalhoso.
- Stripe Payment Link: link fixo; código entregue após o pagamento. O e-mail
  estático de confirmação não serve como código único; automação real exige
  webhook.
- Ko-fi shop: item "apoio" + entrega manual; Edge Function + webhook automatiza
  de ponta a ponta, mas põe segredo e manutenção no servidor.

**Nunca** colocar chave secreta (service_role, secret do Stripe) no APK/EXE; o
projeto já trata `service_role` como segredo de servidor
(`docs/SETUP-INICIAL.md:237-240`).

### 1.3 Cache local, offline e revogação

Leitura: `select plan, granted_at, expires_at from supporters where user_id = auth.uid()` — uma linha, sem RPC. Cache no desktop na tabela `setting` (`Db.kt:63`), no padrão de `account.session` (`Account.kt:204-229`); no Android no `SessionStore` (padrão em `SessionStore.kt:105-108`).
Campos: `plan`, `grantedAt`, `expiresAt`, `lastVerifiedAt`, `codeHint` (4 primeiros caracteres, só para o usuário reconhecer). Offline: ativo se `plan != null`, não revogado e `agora - lastVerifiedAt < 30 dias`; revalida no ciclo de sync existente (`Account.kt:117-124`, `AuthViewModel.kt:509-517`).
`revoked_at` derruba na próxima verificação (atraso de até 30 dias, aceitável para cosmético). Sem conta não há entitlement sincronizado: exigir conta gratuita para resgatar; quem não quer conta continua com o app completo no essencial.

### 1.4 Recursos, custo e risco

| Recurso | O que muda | Esforço | Custo/risco | Recomendação |
|---|---|---|---|---|
| Blocos do perfil reordenáveis/ocultáveis | `statsOrder`/`statsHidden` já existem (`Profile.kt:20-21`); estender aos blocos | M | skew de versão; nada de servidor | entra |
| Larguras e alinhamento | campos opcionais + UI nas duas plataformas | M | quebra em tela pequena | entra com presets |
| EQ 5 → 10 bandas | Android depende do efeito do aparelho; desktop é livre | G no Android | promessa inconsistente | só como "bandas do aparelho" |
| RGB livre + gradiente de 2 cores | novo resolvedor de cor; hoje há 5 presets | M | contraste e skew | entra |
| Recap com gráficos + export 1080×1920 | dados locais já existem (`Recap.kt:25-33`); render offscreen + share | M | nenhum custo de servidor | entra |
| GIF no perfil/capa/playlist | `coil-gif` + upload no bucket público | M | storage/egress e moderação; desktop fica estático | entra com limite duro |
| Clipe de 15 s do vídeo | exige stream de vídeo, que o extrator não pede | G | jurídico, técnico e banda | não entra no v1 |

Regra de produto: o gratuito continua completo no essencial (busca, playlists,
histórico, rádio, downloads, sync, EQ de 5 bandas e recap básico). O apoiador
compra **cosméticos e extras de análise**, nunca limite de uso de música.

### 1.5 Preço e o que não prometer

- **R$ 39,90 uma vez** (~US$ 7-8) pelo pacote cosmético da tabela, exceto GIF.
- **Não prometa** "todos os recursos futuros" (vitalício de features futuras é
  dívida sem teto), hospedagem ilimitada de GIF/clip, clipe de vídeo, download de
  vídeo, letra via API paga, notificação animada, EQ de 10 bandas em todo aparelho,
  nem "sem anúncio" (não há anúncio). Um GIF de 2 MB visto 10 mil vezes são 20 GB
  de egress: se GIF entrar, limite a 1,5 MB e 1 por conta, ou cobre apoio anual de
  R$ 19,90 para manter o Storage.
- Custo de referência: Pro do Supabase ~US$ 25/mês (confirme no painel). Duas ou
  três doações pagam um ano; cem apoiadores com GIF e sync 24/7 não pagam. O preço
  só fecha depois da correção de egress da seção 7.

## 2. Temas e cores: de 5 presets para RGB + gradiente

**Hoje.** `ColorPalette` tem 5 ids e **existe em duas cópias independentes**: desktop em `src/main/kotlin/com/openfy/core/Preferences.kt:4-15` e Android em `androidApp/.../core/Appearance.kt:3-14`.
`ProfileExtras.accent` é validado contra a enum (`Profile.kt:29-30`, `ProfilePrefs.kt:27-28`); id desconhecido vira `openfy`, então "custom" não pode ser só mais um id.
A UI pinta o destaque com `when` fixo (`App.kt:3241-3247`, `OpenfyApp.kt:2836-2843`), e a paleta global também (`Theme.kt:88-117`, Android `ui/theme/Theme.kt:100-129`).
O gradiente de marca já deriva de `accent`/`accent2` (`Theme.kt:40-48`), e o perfil já tem `style.coverGradient` (`Profile.kt:65-70`).

**Extensão.** Campos novos, opcionais: `accentHex: String? = null` (hex
`#RRGGBB` que sobrepõe o preset), `accent2Hex: String? = null` (segunda cor do
gradiente) e `gradientAngle: Int = 0` (0..360; 0 = vertical atual). Em
`sanitized()` (nas duas cópias): aceitar só `^#[0-9A-Fa-f]{6}$` e normalizar para
maiúsculas (cada canal fica 0-255 por construção); `gradientAngle` limitado a
0-360; hex inválido vira `null` e cai no preset. Manter `accent` como
fallback/contraste — não usar `accent = "custom"`, porque cliente antigo sanitiza
para `openfy` e perde a escolha. Só aplicar o custom com entitlement ativo; ao
expirar, manter a leitura (perfil de terceiro não quebra) e bloquear edição.

**Risco de versão antiga.** Todos os `Json` usam `ignoreUnknownKeys = true`
(`Account.kt:83`, `AuthViewModel.kt:378-381`, `Supabase.kt:278`,
`SupabaseApi.kt:32-36`), então **ler** payload novo não quebra. Mas todos usam
`encodeDefaults = true`: um PC antigo que salvar o perfil reemite o `ProfileExtras`
só com os campos que conhece e **apaga** os novos. Mitigação: gravar o tema em
linha separada, `type = profile_theme`, com `{v:1, accentHex, accent2Hex, angle}`,
e fazer merge na leitura; cliente antigo não toca nela. Custa uma request a mais
no load (save é raro) e evita perda silenciosa.

**Validação.** Contraste de `accent` com `ink`/`textHigh` (`Theme.kt:16-20`);
rejeitar luminância próxima. Gradiente no `linearGradient` com ângulo
(`Theme.kt:40`), reaproveitando `coverGradient` no perfil; tema global viaja em
`SyncedSettings.palette` (`SyncedSettings.kt:13`/Android `:11`).

## 3. EQ com mais canais

**Android.** O app usa `android.media.audiofx.Equalizer` preso à sessão de áudio
(`EqualizerController.kt:23-35`). O número de bandas vem do efeito do aparelho em
runtime (`numberOfBands`, `:120`), com faixa de ganho (`bandLevelRange`,
`:121-123`) e frequências centrais (`getCenterFreq`, `:128`). Há 5 alvos fixos
(`:137`) e 5 ganhos (`:145`), mapeando cada alvo para a banda mais próxima; se o
aparelho tem menos de 5, alguns alvos são descartados (`:129-133`). **Não é
possível pedir 10 bandas ao efeito do sistema**: o HAL expõe o que o fabricante
implementou; 3, 5 ou 10 variam por aparelho. Para 10 bandas consistentes seria
preciso DSP próprio — `AudioProcessor` do Media3 com 10 biquads ou
`DynamicsProcessing` (API 28+); o `minSdk` é 26
(`androidApp/app/build.gradle.kts:38`), então API 26-27 precisa do `Equalizer`
atual como fallback. O `LoudnessEnhancer` que compensa o volume perdido
(`:30-34`, `:107-113`) interage com qualquer DSP novo: recalibrar, e testar CPU,
bateria, clipping, fone e Bluetooth.

**Desktop.** O mpv aceita cadeia arbitrária de filtros FFmpeg: `setAudioFilters` monta `lavfi=[...]` (`Mpv.kt:276-284`) e `setEqualizer` gera um `equalizer=f=...:t=q:w=1:g=...` por banda (`Mpv.kt:286-293`). Hoje são 5 frequências (`Player.kt:1137`); passar para 10 é só mudar lista e UI, com CPU irrelevante em desktop.

**Fallback obrigatório.** `SyncedSettings.eqGains` é lista de 5 (`SyncedSettings.kt:20`, Android `:18`); o Android **descarta** o sync se o tamanho não for 5 (`AuthViewModel.kt:1125`); o desktop só restaura se bater com `EQ_FREQS` (`Player.kt:970`); e o controlador tem `BANDS = 5` fixo (`EqualizerController.kt:145`).
Se um cliente mandar 10 ganhos sem mudar o formato, o outro zera o EQ. Solução: `eqBands`/`eqFreqs` opcionais, com "se não bater, mapeia por frequência, avisa na UI" e presets reamostrados por interpolação (não truncados).

## 4. Recap ampliado e compartilhamento

**Já existe.** `RecapSnapshot` (`Recap.kt:25-33`) carrega resumo do mês e do anterior, top faixas, top artistas, dias, faixas ouvidas pela primeira vez e curtidas.
O resumo tem plays, ms, faixas/artistas únicos, completas, puladas, primeira e última escuta (`Recap.kt:4-17`); tudo é calculado localmente no desktop (`Db.kt:895-1059`) e exibido em `RecapScreen` (`App.kt:2896`).
No servidor, `monthly_listening`, `monthly_top_tracks` e `monthly_top_artists` (`0001_initial.sql:994-1031`) dão mês, minutos, plays, faixas únicas, completas/puladas, top faixas e top artistas; o desktop usa o remoto só quando tem mais plays que o local (`AppController.kt:1591-1602`).

**Falta.**

| Dado | Situação | Como obter |
|---|---|---|
| Streak/dias ativos | não existe no servidor; `RecapDay` só no desktop (`Recap.kt:23`) | calcular do diário no cliente |
| Gêneros | **impossível**: `tracks` não tem gênero (`0001_initial.sql:43-52`; já registrado em `docs/PERFIS-SOCIAL-DISCORD.md:195`) | exigiria enriquecer metadados; não prometer |
| Horários | `started_at` existe (`0001_initial.sql:107`) | view `monthly_hours` ou agregar no cliente |
| Comparação anual | não existe | somar 12 meses de `monthly_listening` ou view `yearly_listening` |
| Gráfico diário no Android | Android só lê views mensais (`AuthViewModel.kt:110-114`) | view `monthly_daily` para não puxar `play_events` cru |
| Capa do top artista | view não devolve thumbnail | usar `tracks.thumbnail` da faixa |

**Imagem 1080×1920 sem servidor.** Android: desenhar um `RecapPoster` fixo em `ImageBitmap`/`Bitmap` via Canvas offscreen; salvar em `cacheDir/share/recap-AAAA-MM.png`; expor com o `FileProvider` existente (`AndroidManifest.xml:40-48`).
O `res/xml/file_paths.xml` só declara `cache/updates/` e `files/downloads/`, então falta `<cache-path name="share" path="share/"/>`; compartilhar com `ACTION_SEND` + `type="image/png"` + `FLAG_GRANT_READ_URI_PERMISSION` (hoje o share sheet só manda texto, `OpenfyApp.kt:5258-5265`).
Desktop: renderizar o mesmo poster com Skia/Compose em `Surface` raster 1080×1920, salvar PNG e oferecer "Salvar imagem" com `JFileChooser` (`App.kt:600-605`).
Nada sobe para servidor: fica em `%LOCALAPPDATA%\OpenfyData`. Custo: 200-600 KB por PNG 1080×1920 (estimativa), sem egress novo; se o dado vier do servidor, são agregados mensais, não `play_events` cru.

## 5. GIFs e clipes de 15 s
### 5.1 GIF/WebP animado

Coil 3.3.0 com `coil-compose` e `coil-network-okhttp`
(`libs.versions.toml:13,40-41`), **sem** `coil-gif`. As capas usam `AsyncImage`
(`OpenfyApp.kt:2960`, `3028`, `5112`); não há `ImageLoader` customizado nem
`Application` no manifesto, então o decoder animado não está registrado. O desktop
decodifica com `org.jetbrains.skia.Image.makeFromEncoded` (`Artwork.kt:93-95`),
que devolve **o primeiro quadro**: GIF fica estático no PC. O upload é cru:
Android aceita GIF e até 8 MB (`AuthViewModel.kt:1447-1461`) e sobe os bytes sem
reencode (`Supabase.kt:468-492`); o desktop escolhe qualquer imagem sem checar
tamanho (`App.kt:3249-3260`). Buckets `covers`/`avatars` são públicos e não têm
limite no SQL (`0001_initial.sql:912-918`); o guia recomenda 2 MB
(`docs/PERFIS-SOCIAL-DISCORD.md:206`), mas o código permite 8 MB.

Para habilitar no Android: `io.coil-kt.coil3:coil-gif:3.3.0`, uma `Application : SingletonImageLoader.Factory` registrando `AnimatedImageDecoder` (API 28+) e `GifDecoder` (API 26-27), e `android:name` no `<application>` (`AndroidManifest.xml:13-20`); decodificar no tamanho de exibição, não em 1080×1920 para um avatar de 56 dp.

| Artefato | Formato | Limite | MB típico |
|---|---|---|---|
| Avatar | WebP/JPEG | 512×512, ≤300 KB | 0,1-0,2 |
| Capa de perfil | WebP/JPEG | 1600×600, ≤500 KB | 0,15-0,3 |
| Capa de playlist | WebP/JPEG | 1200×1200, ≤500 KB | 0,15-0,4 |
| GIF/animado | WebP animado (GIF de fallback) | 480×480, ≤6 s, ≤1,5 MB | 0,6-1,5 (GIF: 1,5-3) |

Cada view baixa do bucket público; com CDN e `?v=` só mudando no upload
(`Supabase.kt:492`), o impacto maior é o primeiro acesso. Ainda assim, 1,5 MB ×
5.000 views/mês = 7,5 GB de egress. GIF deve ser o último recurso a entrar e com
teto por conta.

### 5.2 Clipe de 15 s

O app só tem áudio: o extrator devolve `stream.audioUrl` (`YouTube.kt:917-953`),
com `allowHls = false` e `allowSabr = false` (`:932-936`), cache de 4 min
(`:1134`). O `StreamServer` do desktop reextrai e proxia Range
(`StreamServer.kt:22-26`, `87-123`), mas só entrega o que o `resolve` devolve.
No Android, `MediaItem.ClippingConfiguration` (Media3 1.8.0,
`libs.versions.toml:12`) corta a reprodução, mas **não gera arquivo**; o
`MediaItem` é montado sem clipping (`AuthViewModel.kt:3474-3480`). Exportar
exigiria `media3-transformer` (não está em `libs.versions.toml:38-39`) sobre um
stream de **vídeo**, que o extrator atual não pede. Range em MP4 progressivo baixa
só o trecho, mas depende de `Range` e de `moov` no início; links do googlevideo
expiram e limitam conexões por assinatura. Custo: 15 s em 480p ~2,8 MB; 720p ~5 MB
(estimativa), baixado de novo por usuário. No desktop, mpv toca trecho com
`--start`/`--end`, mas o app só manda `start=` (`Mpv.kt:253-264`) e roda com
`--no-video` (`:81`), então não há preview; mpv grava com `--o=arquivo`, o que vira
conversor escondido, aumenta suporte e não resolve direitos autorais.

Recomendação: no v1 do apoiador **não** prometer clipe de vídeo. Se quiser
"trecho", entregue 15 s **do áudio** (clipping no Media3; `--start/--end` no
desktop) com capa estática.

### 5.3 Notificação e widget

Ícone grande de notificação é `Bitmap` estático: o serviço baixa a arte em 512×512
(`PlaybackService.kt:220-233`) e aplica com `setLargeIcon` (`:172`); `MediaStyle`
não anima (`:175-179`). Não há Glance/AppWidget no manifesto
(`AndroidManifest.xml:32-48`). Alternativa honesta: frame estático do trecho com
selo "clipe" e ação "Ver clipe" abrindo o app via `PendingIntent` para
`MainActivity` (`PlaybackService.kt:139-146`); widget, se existir, também usa
`RemoteViews` e não anima GIF de forma confiável.

## 6. Peso atual (medido)

### 6.1 Android

APK 0.7.1: **18.393.592 bytes (17,54 MiB)**.

| Grupo | Arquivos | Comprimido |
|---|---|---|
| `classes*.dex` (4 arquivos) | 4 | 16,61 MiB (59.483.940 B crus) |
| `res` + `resources.arsc` | 150 | 0,83 MiB |
| `lib` | 4 | 0,04 MiB |

DEX cru: 56,7 MiB; comprimido, 16,61 MiB — **95% do APK**. `lib/` é só
`libandroidx.graphics.path.so` nas 4 ABIs, 0,04 MiB: **ABI split não resolve
nada**. `materialIconsExtended` está ligado (`androidApp/app/build.gradle.kts:103`)
e `isMinifyEnabled = false` (`:59-64`), então o D8 empacota todas as classes do
artefato. Medido: 11.416 referências a `Landroidx/compose/material/icons/` no
`classes.dex` (contra 51 imports no desktop e 40 no Android). O JAR desktop do
mesmo artefato tem **36,02 MiB**. Estimativa: 20-30 MB do DEX cru são ícones, ou
6-8 MiB comprimidos.

| Corte | Ganho | Esforço | Risco |
|---|---|---|---|
| R8 (`isMinifyEnabled=true`) + keep rules | APK ~8-11 MiB (estimativa) | P | serialização/Ktor/Media3/InnerTube por reflexão; testar release |
| Trocar `material-icons-extended` por ícones usados/core | 6-8 MiB no APK (~25 MB de DEX) (estimativa) | M | refazer imports |
| ABI split | ~0,04 MiB | P | ganho irrelevante |
| `shrinkResources` | <0,5 MiB | P | recurso acessado por nome |
| Remover `ui-tooling-preview` do release | pequeno | P | nenhum (`:105`) |

### 6.2 Desktop

| Artefato | Tamanho |
|---|---|
| `dist/Openfy-0.3.0.exe` | 175.213.568 B (167,1 MiB) |
| `dist/Openfy-0.2.9.msi` | 174.498.945 B (166,4 MiB) |
| Imagem de app no disco | 308,1 MiB |
| `resources/` (mpv + d3dcompiler) | 119,8 MiB |
| `app/` (JARs + classes, sem resources) | 110,2 MiB |
| `runtime/` (JRE jlink, `lib/modules` = 55,02 MiB) | 78,2 MiB |
| `mpv.exe` | 121.112.576 B (115,5 MiB) |
| `d3dcompiler_43.dll` | 4.481.992 B (4,27 MiB) |
| `material-icons-extended-desktop.jar` | 37.768.805 B (36,02 MiB) |

O `mpv.exe` ocupa 115,5 MiB no disco e é o maior item do image; no EXE de 167,1
MiB ele entra comprimido e já é conhecido como não versionado por passar de
100 MB (`resources-dist/windows/README.md:3`). O JRE é enxuto por `modules(...)`
(`build.gradle.kts:114`), mas `modules` ainda tem 55 MiB.

| Corte | Ganho | Esforço | Risco |
|---|---|---|---|
| mpv como download opcional no 1º uso | 50-70 MiB no EXE (estimativa) | M | hash/assinatura, rede, usuário sem player |
| Remover `materialIconsExtended` | ~10-14 MiB no EXE (estimativa) | M | mesma troca de ícones |
| `jlink --compress`/revisar módulos | 15-25 MiB no runtime (estimativa) | M | start e compatibilidade |
| MSI em vez de EXE | ~0,7 MiB | nenhum | mantenha os dois |

## 7. Banco de dados: capacidade, crescimento e egress

### 7.1 O que o schema já tem

`play_events` tem índices por `(user_id, started_at desc)` e `server_rev`
(`0001_initial.sql:102-118`, `664`, `675`) e trigger de revisão em toda escrita
(`:163-174`), mas **não tem índice para o pull por `updated_at`**, exatamente a
query usada (`SupabaseApi.kt:369-375`). `tracks` é catálogo global e qualquer
autenticado pode inserir (`0001_initial.sql:730-737`) — vetor de poluição.
Preferências vão em `activities` (`Profile.kt:8-11`, `Account.kt:476-485`); como o
trigger proíbe alterar o payload (`0001_initial.sql:463-486`), cada save faz
INSERT de uma linha e DELETE da anterior (`Supabase.kt:397-401`,
`Account.kt:478-484`): uma linha por usuário na prática, mas com churn e duas
requests. `activities` está na publicação de Realtime (`0001_initial.sql:975`), e
as views de recap são views comuns sobre `play_events`
(`0001_initial.sql:994-1031`): apagar eventos sem arquivar quebra meses passados.

### 7.2 Crescimento medido e estimado

Medição local (cópia do `openfy.db`, `VACUUM`, linhas sintéticas iguais às reais):
os lotes de 1.000, 4.000 e 5.000 linhas cresceram 253.952, 970.752 e 1.228.800
bytes — **~245 B por evento** no SQLite local. No Postgres, com tuple header,
índices e MVCC, use **~0,3 KB por evento** (estimativa). Daí: 1.000 plays/mês ≈
3,6 MB/ano; 5.000 plays/mês ≈ 18 MB/ano; 10 playlists + 1.000 itens ≈ 130 KB por
conta; prefs ≈ 3-4 KB (`Profile.kt:58-62`); 2 imagens ≈ 0,5 MB; 1 GIF = 1,5-3 MB.

### 7.3 Por que egress vem antes de storage

Os dois clientes puxam o conjunto inteiro de playlists, itens, curtidas e faixas a
cada 120 s: desktop em `SyncEngine.kt:38-56` + laço de `Account.kt:117-124`;
Android em `AuthViewModel.kt:753-783` + laço de `:509-517`. Só `play_events` é
incremental por `updated_at` (`SyncEngine.kt:190-206`,
`AuthViewModel.kt:1005-1041`); os outros não têm cursor. Conta de pão (estimativa;
~0,5 KB de headers por request):

| Cenário | Conteúdo | Por poll | Por mês (30 d, 1 dispositivo) |
|---|---|---|---|
| Casual | 3 playlists, 150 itens, 100 curtidas, 200 faixas | ~61 KB | ~1,3 GB |
| Pesado | 10 playlists, 1.000 itens, 800 curtidas, 1.500 faixas | ~429 KB | ~9,3 GB |
| Após pull incremental | só o que mudou | ~2-5 KB | ~0,1 GB |

Referência de plano (valores publicados; **confirme no painel**): Free ~500 MB de
banco, 1 GB de Storage, 5 GB de egress; Pro ~8 GB, 100 GB e 250 GB. Com o código
atual, o Free atende **1-2 casuais** e o Pro **~25 pesados ou ~90 casuais** por
egress. Depois do pull incremental e de intervalo maior, o Pro passa a milhares, e
o limite vira banco/Storage (estimativas). Storage: 1.000 contas com avatar+capa =
~0,5 GB; com GIF de 1,5 MB = +1,5 GB; o **egress de imagem** pode superar o de
banco se o GIF for grande.

### 7.4 Correções priorizadas

| # | Correção | Ganho estimado | Esforço |
|---|---|---|---|
| 1 | Pull incremental por `server_rev`/`updated_at` em playlists, itens, curtidas e faixas, cursor em `sync_state` (`Db.kt:139-141`) | 90-95% do egress de sync | M |
| 2 | Índices: `(user_id, updated_at)` em `play_events`, `(owner_id, updated_at)` em `playlists`, `(playlist_id, updated_at)` em `playlist_items`, `(user_id, updated_at)` em `likes` | evita scan/sort a cada 2 min | P |
| 3 | Intervalo de 120 s para 300-900 s + sync por evento (debounce já existe, `Account.kt:127-136`) | 60-90% do egress restante | P |
| 4 | Tabela `user_prefs` (1 linha/usuário, `profile_extras` e `settings` jsonb) em vez de INSERT+DELETE | tira churn, 1 request, sai do Realtime | M |
| 5 | Reencode/limpeza de imagem: WebP/JPEG ≤500 KB no cliente e apagar objeto antigo (hoje o nome tem timestamp e o antigo fica, `Supabase.kt:483-492`) | Storage previsível | P |
| 6 | Retenção de `play_events` com arquivo mensal antes de apagar e limite de inserção em `tracks` (`0001_initial.sql:735-737`) | controla banco e catálogo | M/P |

O push de eventos já vai em lote de 200 (`Db.kt:863`); o problema é o primeiro pull, que pede 5.000 linhas de uma vez (`SyncEngine.kt:192`) — paginar em 1.000 evita um pico de ~1,5 MB por conta.

### 7.5 "Aguenta mais usuários?"

**Hoje, no Free: não.** O egress de um usuário com PC aberto o dia inteiro já
consome parte dos 5 GB, dois dispositivos passam de 2,5 GB/mês só de metadados
(estimativa), e o projeto Free pausa por inatividade. **No Pro: 25-90 usuários**
conforme o perfil (estimativa), com banco tranquilo por 1-3 anos. **Depois dos
itens 1-3: 1.500-3.000 casuais no Pro** (estimativa), limitado por Storage se GIF
entrar. Conclusão: **o banco aguenta; o sync é que não aguenta.** Corrija o pull
antes de vender apoio, porque cada apoiador com GIF aumenta o custo recorrente que
a doação única não cobre.

## 8. Roadmap curto

Esforço: P ≤ 3 dias, M 1-2 semanas, G ≥ 3 semanas.

| Ordem | Item | Esforço | Por quê |
|---|---|---|---|
| 1 | Pull incremental + índices + intervalo maior (7.4 itens 1-3) | M | sem isso, apoio aumenta prejuízo |
| 2 | Entitlement: migration 0004, RPC, cache offline, tela "Apoiador" | M | valida demanda sem cosmético |
| 3 | R8 + poda de ícones | P | corta o APK quase pela metade |
| 4 | Tema RGB + gradiente (linha `profile_theme`) | M | cosmético barato |
| 5 | Recap com gráfico + export 1080×1920 | M | usa dados que já existem |
| 6 | Prefs em tabela própria | M | limpa churn antes de crescer |
| 7 | GIF/WebP animado com 1,5 MB e 1 por conta | M | só com Storage/egress sob controle |
| 8 | EQ 10 bandas no desktop; Android "bandas do aparelho" | M/G | evita promessa falsa |
| 9 | Clipe de 15 s de vídeo | G | reavaliar depois de 1-8 |

## Apêndice: método

Tamanhos de `dist/`, `resources-dist/` e `build/compose/binaries` medidos com
`Get-Item`/`Measure-Object`; APK lido como ZIP (soma de `Length` e
`CompressedLength`); ícones contados como `Landroidx/compose/material/icons/` nos
`classes*.dex`; bytes/evento medidos em cópia do `openfy.db` com 10.000 linhas;
egress = payload × 30 polls/h × 24 h, com as premissas de 7.3.
