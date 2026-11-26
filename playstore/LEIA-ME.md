# Publicar na Play Store — pacote pronto

Versão 0.1.0 (versionCode 1) · pacote `com.murilo.bloqueadorreels` · minSdk 26 · targetSdk 36

## O que está pronto (nesta pasta)

| Arquivo | Para quê |
|---|---|
| `bloqueador-reels-v0.1.0.aab` | O que a Play recebe (App Bundle de release, assinado com a chave de upload). SHA-256: `a64770ee…c08626e` |
| `ficha-da-loja.md` | Nome, descrição curta e completa, notas da versão, categoria |
| `graficos/icone-512.png`, `graficos/imagem-de-destaque-1024x500.png` | Gráficos da ficha |
| `screenshots/01, 01b, 02 a 06` | 7 screenshots (1080×2160, dentro do limite de 2:1): aviso (com e sem os botões), serviço desativado, bloqueio ativado, modo escuro, política e termos |
| `docs/` (raiz do repo): `index.html`, `politica-de-privacidade.html`, `termos-de-uso.html` | Site publicado pelo GitHub Pages, gerado por `playstore/gerar-html.ps1` dos mesmos textos que o app mostra |
| `declaracao-acessibilidade.md` | Respostas do formulário de Acessibilidade + roteiro de vídeo |
| `seguranca-dos-dados-e-classificacao.md` | Segurança dos dados, classificação, público-alvo, anúncios |
| `video-consentimento.mp4` | Vídeo do aviso, política, recusa, aceite e ativação (56 s) |
| `video-funcionamento-LOCAL-feed-real.mp4` | Rascunho do app em uso. **Não suba como está**: mostra feed real |

Mudanças no app para a Play: botão **"Não aceito"** no aviso, serviço inerte sem consentimento, `isAccessibilityTool="false"`, ícone adaptativo, eventos e flags de acessibilidade reduzidos ao mínimo usado, lint sem erros, 32 testes unitários passando, política de privacidade e termos de uso dentro do app (rodapé do aviso e da tela principal).

## O que só você pode fazer

1. **Conta de desenvolvedor** em play.google.com/console: US$ 25, verificação de identidade (documento, telefone, endereço).
2. **Política e termos já estão hospedados** no GitHub Pages: https://murilsanches.github.io/bloqueador-reels/politica-de-privacidade.html (use esta URL na ficha da loja e na Segurança dos dados). Se mudar nome/e-mail ou os textos: edite `strings.xml`/os `.md` de `app/src/main/assets`, rode `powershell -File playstoregerar-html.ps1 -Responsavel "Nome" -Contato "email"`, faça `git push` e recompile o `.aab`.
3. **Teste fechado: 12 testadores por 14 dias seguidos** (conta pessoal criada depois de 13/11/2023). Convide pessoas com conta Google pelo e-mail; elas aceitam o link e instalam. Só depois o botão de produção libera. Conta de organização (com D-U-N-S) é isenta, segundo fontes secundárias.
4. **Vídeo da declaração**: suba `video-consentimento.mp4` no YouTube como *Não listado*. Se quiser mostrar o app em uso, regrave a parte do Instagram com uma conta de teste (roteiro em `declaracao-acessibilidade.md`).
5. **Colar os textos e responder os formulários** da Play Console com os arquivos desta pasta.
6. **Backup da chave de assinatura**: `release.keystore` e `keystore.properties` na raiz do projeto. Guarde fora do projeto (cofre de senhas/nuvem privada). Com o Play App Signing ela vira a *chave de upload*; se perder, dá para pedir reset à Play, mas dá trabalho.

## Passo a passo na Play Console

1. Criar app → nome "Bloqueador de Reels", idioma pt-BR, **App**, **Gratuito**.
2. Conteúdo do app: política de privacidade, anúncios (não), acesso ao app (sem login), classificação (IARC), público-alvo (13+), segurança dos dados, **API de Acessibilidade** (declaração + vídeo).
3. Presença na loja: colar `ficha-da-loja.md`, enviar ícone, imagem de destaque e screenshots.
4. Testes › **Teste fechado** › criar faixa › enviar o `.aab` › adicionar testadores › publicar o teste.
5. Aguardar os 14 dias com 12 testadores ativos → **Solicitar acesso à produção**.
6. Produção › enviar o mesmo `.aab` (ou a versão mais nova) › enviar para revisão.

## Riscos e expectativas

- **A revisão da Acessibilidade é o ponto mais incerto.** O app não é ferramenta para pessoas com deficiência, então cai na categoria mais rigorosa. A política de outubro de 2025 proíbe uso da API para ações autônomas (agentes de IA); o app faz um "voltar" automático quando o Reels abre. A declaração explica isso, mas **não há garantia de aprovação**. Se recusarem, o Google informa o motivo e dá para ajustar e reenviar.
- Revisão pode levar de dias a semanas na primeira vez.
- Mudar o que o app faz com a API exige reenviar a declaração.
- Plano B sem Play: distribuir o APK assinado (já funciona, ver `CLAUDE.md`).

## Para gerar um novo `.aab`

`./gradlew.bat bundleRelease --no-daemon` → `app/build/outputs/bundle/release/app-release.aab`. Aumente `versionCode` em `app/build.gradle.kts` a cada envio (a Play recusa repetir).
