# Ciclo 1 — `BarraOverlay`, `AppAlvo` e sinal reserva de Reels

Data: 2026-11-27 · Projeto: `bloqueador-reels` · Próximos ciclos: 2 (temporizador Instagram), 3 (YouTube Shorts)

## Objetivo

Preparar o código para receber outras plataformas (YouTube, TikTok) e o temporizador, sem mudar o que o usuário vê hoje. Hoje o `ReelsBlockerService` (600 linhas) mistura ciclo de vida, vigia de janela, bloqueio, regras de link/chat, saúde e ~250 linhas de desenho da barra substituta, tudo preso ao Instagram (`PKG_INSTAGRAM`, `assinaturas.json`, `packageNames` no XML). Além disso, a detecção de Reels depende de um único ID (`clips_viewer_view_pager`).

## Escopo

Entra:
1. Extrair o desenho da barra para `BarraOverlay` (sem conhecimento de Instagram).
2. Criar o contrato `AppAlvo` e a implementação `InstagramAlvo`.
3. Sinal reserva de detecção de Reels que também bloqueia.

Fica fora: temporizador (ciclo 2), YouTube/TikTok (ciclo 3), qualquer mudança em política, termos, aviso, declaração de Acessibilidade ou ficha (o serviço continua lendo só o Instagram, com os mesmos eventos e flags).

## Design

### `BarraOverlay`
- Recebe `EstadoBarra` (bounds, botões, aba ativa, badges, tema) e um `EstiloBarra` (por chave de botão: ícone ativo/inativo e descrição; cores de fundo/pílula por tema).
- API: `mostrar(estado)`, `atualizar(estado)`, `remover()`, `val visivel`, callback `aoTocar(chave, acao)` com `acao` = clique ou toque longo.
- Guarda a view na `WindowManager` (`TYPE_ACCESSIBILITY_OVERLAY`) e o último estado desenhado.
- Mantém as regras atuais: atualização simples nunca faz remove+add (`mesmaEstrutura`/`atualizarBarra`); barra vertical se o container for mais alto que largo; foco de teclado desligado.
- Não conhece Instagram: ícones e descrições vêm do `EstiloBarra`, que o `AppAlvo` fornece.

### `AppAlvo`
Contrato (Kotlin interface):
- `pacote: String`
- `estilo: EstiloBarra`
- `lerBarra(root, modoEscuro): LeituraBarra?` (hoje `BarraReader.ler`)
- `telaVideoCurto(root): String?` — devolve o ID/sinal que casou, ou `null`.

`InstagramAlvo` recebe o que hoje é fixo: `PKG_INSTAGRAM`, `Assinaturas`, `BarraReader`, ícones `ic_nav_*`, e implementa `telaVideoCurto` com o sinal principal e o reserva.

A lógica de link/chat (`reelsPermitido`, `entradaExterna`, `liberadoPorLink`) continua no serviço porque só existe no Instagram; se o YouTube precisar dela, extrai-se no ciclo 3.

### Sinal reserva
`telaVideoCurto` devolve não nulo se:
- o ID principal (`reelsViewerIds`) está visível; **ou**
- pelo menos `Decisoes.MIN_RESERVA` (2) IDs de `reelsReservaIds` (novo campo em `assinaturas.json`, no máximo 4) estão visíveis ao mesmo tempo.

Dois IDs independentes juntos reduzem o falso positivo. Não se usa a ausência de `tab_bar` como condição: o Reels aberto pela aba Reels mantém a barra visível. `reelsReservaIds` passa por `Assinaturas.valida()` (só IDs `com.instagram.android:id/`). A regra de combinação é função pura em `Decisoes.kt`, testada na JVM. Os IDs vêm de `planejamento/medicoes/2026-11-27-reels-ids.md`.

### `ReelsBlockerService` depois
Fica com ciclo de vida, eventos, vigia de janela, bloqueio (`BACK`/`HOME`), link/chat e saúde. Usa `InstagramAlvo` e `BarraOverlay`. O `accessibility_config.xml` continua com `packageNames="com.instagram.android"`.

## Compatibilidade e riscos
- Comportamento visível idêntico ao atual: bloqueio por `BACK`/`HOME` com as mesmas janelas de tempo, barra sem piscar, toque longo, badges, tema, link/chat.
- Risco de regressão no desenho (piscar) ao mover código: coberto pela verificação no emulador.
- Risco de falso positivo do reserva: mitigado por exigir dois IDs exclusivos do Reels juntos; se a medição no emulador mostrar bloqueio indevido, o reserva fica só como aviso de saúde até ajustar.

## Testes
- Os 32 testes atuais continuam passando (`./gradlew.bat testDebugUnitTest`).
- Novos testes JVM: combinação principal/reserva (principal visível conta; 2 IDs de reserva juntos contam; 1 ID de reserva sozinho não conta; nenhum sinal não conta), e `Assinaturas.valida()` rejeitando `reelsReservaIds` fora do prefixo.
- Emulador (`Medium_Phone_API_37.0`): `instagram://reels_home` bloqueia por `BACK`; trocar de aba não pisca a barra; toque longo no Perfil abre o seletor de contas; link/chat com as opções ligadas; claro e escuro.

## Pronto quando
Testes passando, `assembleDebug` e `lintRelease` sem erro, verificação no emulador sem regressão, `ReelsBlockerService` sem código de desenho de barra e sem referência direta a `PKG_INSTAGRAM` fora do `InstagramAlvo`.
