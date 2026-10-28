# Anúncios no Openfy: avaliação

Data: 2026-10-28.

## Resumo

**Não recomendo anúncios ocultos/carregados atrás da interface.** Além de não
gerarem receita confiável, violam as políticas do Google e podem causar perda da
conta, estorno para anunciantes e bloqueio do app.

## Por que anúncios invisíveis não funcionam

- O AdSense exige que o anúncio esteja **visível** para o usuário. Impressões
  geradas por anúncios escondidos, fora da viewport, cobertos ou apenas
  carregados em background são consideradas **impressões inválidas**.
- No Android, o produto correto seria o **AdMob**, não o AdSense. O AdMob também
  exige posicionamento visível e não permite impressões artificiais.
- A receita é medida por impressão/clique válidos. Um anúncio escondido não
  conta ou conta como tráfego inválido; o resultado prático tende a ser receita
  próxima de zero e risco alto.
- Carregar SDK de anúncios em background adiciona peso, consumo de bateria,
  rede, rastreamento e obrigações de consentimento (LGPD/GDPR), sem retorno.
- A conta de anúncios pode ser suspensa, e o app pode ser removido da loja por
  práticas enganosas.

## Alternativas honestas

1. **Anúncio visível e discreto** em uma área que não atrapalhe o player, como
   Ajustes ou a tela "Sobre". Um banner pequeno, com consentimento e opção de
   remover.
2. **Apoio voluntário**: Pix, GitHub Sponsors, Ko-fi ou "Apoie o Openfy". É o
   que menos pesa e não interfere no uso.
3. **Recursos premium**: temas extras, qualidade máxima, downloads em lote,
   sincronização avançada ou estatísticas. Modelo mais coerente com um tocador
   pessoal.
4. **Patrocínio/afiliados** claramente identificados, por exemplo links para
   lojas de música ou equipamentos.

## Recomendação

Se o objetivo é monetizar sem incomodar, a ordem é: apoio voluntário →
recursos premium → banner visível e discreto. Anúncios ocultos não entram.

## Aspecto técnico

O Openfy Android já usa Media3, Coil, Ktor e um serviço de reprodução. Um SDK de
anúncios adicionaria dependência, permissões, consentimento e mais um ciclo de
vida para gerenciar. Não há ganho de arquitetura em esconder o anúncio: o
anúncio precisa ser renderizado e visível para ter valor.
