# Compartilhar playlist: ouvir, salvar cópia ou colaborar

Um link de compartilhamento (`https://leo-bsb.github.io/openfy/join.html?code=…`)
carrega apenas o **código** da playlist. Quem recebe escolhe o que fazer dentro
do Openfy:

| Ação | Precisa de conta? | O que acontece |
|---|---|---|
| **Ouvir** | desktop: não; Android: sim (o app já exige conta para tocar) | As faixas entram na fila do player; nada é gravado na conta e a original não muda. |
| **Salvar cópia** | sim no Android; não no desktop | Cria uma playlist independente com as mesmas faixas. |
| **Colaborar** | sim | Comportamento antigo do `join_playlist`: entra como editor e sincroniza alterações. |

## Onde fica na interface

- **Desktop:** `Biblioteca → Compartilhar → Copiar link de compartilhamento`.
  Para entrar, `Importar, backup e colaboração → Compartilhar ou entrar por
  código`: cole o código/link, toque em **Ver playlist** e escolha uma das três
  ações.
- **Android:** `Playlist → Compartilhar` gera o código/link. Para entrar,
  `Biblioteca → Entrar com código`: cole, toque em **Ver** e escolha a ação.
- **Site:** `site/join.html` explica as três opções e mostra o código para colar
  no app.

## Migration 0003 (obrigatória para a prévia)

`supabase/migrations/0003_shared_playlist_preview.sql` cria a RPC
`public.shared_playlist_preview(p_code text)`, `SECURITY DEFINER`, que devolve
somente metadados públicos e a lista de faixas de uma playlist cujo
`share_code` foi informado. Ela:

- **não** altera tabelas, policies, triggers nem RPCs existentes;
- **não** cria membership (continua sendo o `join_playlist` que faz isso);
- **não** expõe `share_code`, e-mails ou ids internos;
- aceita chamadas `anon` (ouvir/salvar cópia sem login).

### Como aplicar

Enquanto não houver CLI linkada, use o **SQL Editor** do projeto
`arxoiibjocazfxtliysc` (role `postgres`):

1. Abra o SQL Editor.
2. Cole o conteúdo de `supabase/migrations/0003_shared_playlist_preview.sql`.
3. Execute. O PostgREST recarrega o schema automaticamente.

A migration 0002 (`0002_security_hardening.sql`) continua **pendente e
independente**; não é necessário aplicá-la para usar a 0003.

### Rollback

```sql
drop function if exists public.shared_playlist_preview(text);
```

Depois do rollback, os apps novos mostram “código inválido” na prévia; ouvir e
salvar cópia deixam de funcionar, mas o modo colaborar continua igual.

## Teste manual

1. Conta A cria uma playlist com 3 faixas e copia o link.
2. Conta B (ou o mesmo aparelho deslogado, para “Ouvir”) cola o link em
   `Entrar com código` → **Ver playlist**: deve mostrar nome, dono e 3 faixas.
3. **Ouvir**: as 3 faixas tocam e a playlist de A não muda.
4. **Salvar cópia**: aparece uma playlist nova, independente; mudar A não muda a
   cópia.
5. **Colaborar**: B entra como editor; adicionar faixa em B aparece em A após o
   sync.
6. Playlist apagada/excluída: a prévia responde “código inválido”.
