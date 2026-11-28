# Ciclo 2 — Temporizador do Instagram (aviso e limite)

Data: 2026-11-27 · Projeto: `bloqueador-reels` · Depende do ciclo 1 (`AppAlvo`, `BarraOverlay`) · Seguinte: ciclo 3 (YouTube Shorts reaproveita tudo)

## Objetivo

Dar ao usuário uma alternativa ao bloqueio total de Reels: permitir ver, com **aviso** de tempo e **limite máximo por dia**. Há dois escopos independentes: **Reels** (só a tela de Reels) e **Instagram inteiro** (feed, stories, Reels e o resto).

## Escopo

Entra: seletor Bloquear/Permitir para Reels; temporizadores de aviso e de limite para cada escopo; contagem local; faixa de aviso; ação ao estourar o limite; tela de configuração; atualização dos textos legais.

Fica fora: YouTube/TikTok (ciclo 3), sincronização, estatísticas históricas, senha/trava para impedir ajustes, notificação do sistema.

## Configuração (`Prefs`)

Por escopo `reels` e `app`:
- `avisoLigado` (padrão `false`), `avisoMin` (padrão 5);
- `limiteLigado` (padrão `false`), `limiteMin` (padrão 30 em `reels`, 60 em `app`).

Só para Reels: `modoReels` = `BLOQUEAR` (padrão, mantém o app como hoje) ou `PERMITIR`.
Com `PERMITIR`, as opções "Reels por link" e "Reels por chat" ficam ocultas e sem efeito.
O temporizador do Instagram inteiro funciona também com Reels em `BLOQUEAR`.
Valores de minutos: 5, 10, 15, 30, 45, 60 (ou ±5). Mudar ou desligar vale na hora.

Estado do dia, por escopo: `usadoMs` e `dia` (data local `yyyy-MM-dd`). Tudo em `SharedPreferences`, local.

## `Temporizador` (lógica pura, sem Android)

Entrada: `tick(escopo, agoraMs, ativo)`. Saída: lista de eventos.
- Um tick por segundo enquanto o Instagram está em primeiro plano. O delta somado por tick tem teto (5 s), para o aparelho dormindo ou o serviço pausado não gerar horas falsas. Tela apagada não conta (`PowerManager.isInteractive`).
- `ativo` por escopo:
  - `reels`: `InstagramAlvo.telaVideoCurto(root) != null` **e** `modoReels == PERMITIR` (ou Reels liberado pelo caminho atual de link/chat).
  - `app`: a janela ativa é o Instagram.
- Virada do dia (data local diferente de `dia`): zera `usadoMs` e libera o limite.
- Eventos:
  - `Aviso(usadoMin, restanteMin?)` quando o acumulado do dia cruza um múltiplo de `avisoMin` (5, 10, 15…); `restanteMin` só existe se o limite está ligado. O aviso é pelo tempo **acumulado no dia**, não pela sessão.
  - `LimiteAtingido` quando `usadoMs >= limiteMin` com o limite ligado; repete a cada novo uso até a meia-noite (o estado "estourado" persiste).

## Aviso na tela

Faixa no topo, janela `TYPE_ACCESSIBILITY_OVERLAY` não tocável (`FLAG_NOT_TOUCHABLE`), ~4 s. Textos: "5 min de Reels hoje · 25 min restantes", "30 min de Instagram hoje". Reaproveita a infraestrutura de janela de overlay do ciclo 1. Sem permissão nova.

## Limite atingido

- **Reels:** volta o bloqueio de hoje (`bloquearReels`, mesmo `BACK`→`HOME`) até meia-noite, com a faixa "Limite de Reels de hoje atingido".
- **Instagram inteiro:** sempre que o Instagram vier para a frente, `HOME` e a faixa "Limite do Instagram de hoje atingido", até meia-noite.
- Se o usuário aumentar o limite ou desligar, o bloqueio do limite cai na hora.

## Tela do app (`MainActivity`)

Cartão "Instagram": seletor Reels Bloquear | Permitir; abaixo, dois blocos (Reels, Instagram inteiro), cada um com interruptor + minutos de aviso e interruptor + minutos de limite, e "Hoje: X min". Em `BLOQUEAR`, o bloco de Reels fica recolhido.

## Privacidade e Play

- Guarda apenas tempo de uso por escopo e a data, localmente. Nenhum conteúdo, nenhuma permissão nova, nenhuma internet.
- Mesmo assim o app passa a medir e agir pelo tempo de uso: atualizar política, termos, aviso (`TelaAviso`), declaração de Acessibilidade e ficha juntos (regra do `CLAUDE.md`), rodar `playstore/gerar-html.ps1`, aumentar `versionCode`.
- `DocumentoTest` precisa continuar passando com os textos novos.

## Testes

- JVM (`Temporizador`, relógio falso): soma só com `ativo`; teto do tick; cruzar 5/10/15 min emite um aviso por múltiplo; limite ligado/desligado; ajustar o limite para cima cancela o estouro; virada de dia zera; escopos independentes; `restanteMin` ausente sem limite.
- Emulador: Reels em Permitir com aviso a cada 1 min (valor de teste só em debug), faixa aparece e some; limite de Reels pequeno volta o bloqueio; limite do app manda para a tela inicial; tela apagada não soma; seletor em Bloquear mantém o comportamento atual.

## Pronto quando
Testes passando, `lintRelease` sem erro, emulador sem regressão no modo Bloquear, textos legais e declaração atualizados, `versionCode` aumentado.
