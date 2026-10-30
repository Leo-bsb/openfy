# Diretrizes do Projeto & Comportamento do Agente

## Autonomia e Execução
- **Ação Direta sem Confirmações**: Não peça confirmação ou permissão antes de ler, criar, editar arquivos, refatorar código ou executar comandos de build/testes seguros no sandbox. Proceda diretamente com a implementação e solução dos problemas.
- **Resolução Ponta a Ponta**: Diagnostique, aplique as modificações necessárias e verifique o resultado antes de responder ao usuário.
- **Exceção para Confirmações**: Peça autorização apenas para ações destrutivas irreversíveis (ex.: exclusão de repositórios/branches remotas, `git reset --hard`) ou quando houver ambiguidade crítica de requisitos de negócio.

## Economia de Tokens e Otimização de Contexto
- **Leitura Cirúrgica de Arquivos**: Nunca leia arquivos inteiros extensos quando precisar apenas de trechos específicos. Use sempre `StartLine` e `EndLine` na ferramenta `read_file`.
- **Filtro de Saída de Comandos**: Para comandos com saídas verbosas (Gradle, logs, diagnósticos), filtre apenas os erros e sumários (ex.: `--quiet`, `Select-String`, filtros) para não poluir o histórico de contexto.
- **Ignorar Artefatos e Dumps**: Nunca inspecione ou leia arquivos de cache/build (`.gradle/`, `build/`, `.tmp/`, `dist/`) nem arquivos de dump de crash (`hs_err_pid*.log`, `replay_pid*.log`).
- **Respostas Concisas**: Seja direto e técnico. Evite preâmbulos de cortesia ("Certamente!", "Com certeza!"), repetições desnecessárias de código e explicações redundantes.
- **Delegação para Subagentes**: Em buscas amplas pelo repositório ou análises exploratórias pesadas, utilize subagentes dedicados para manter o contexto da conversa principal limpo.
