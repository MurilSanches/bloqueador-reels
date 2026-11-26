# CLAUDE.md

App Android (Kotlin, minSdk 26) que esconde a aba Reels do Instagram via `AccessibilityService`. Spec: `App Android Bloqueador de Reels do Instagram.md` (fonte de verdade; segue o roteiro de 8 passos).

## Build (sem Android Studio, sem JDK no PATH)

```
JAVA_HOME="C:\Program Files\Unity\Hub\Editor\6000.6.2f1\Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK"  (JDK 17)
./gradlew.bat testDebugUnitTest assembleDebug --no-daemon
```
APK: `app/build/outputs/apk/debug/app-debug.apk`. SDK em `local.properties` (não versionado). Versões travadas por compatibilidade: Gradle 9.3.1, AGP 9.1.1 (Compose 1.12 exige AGP >= 9.1; AGP 9.2+ exige Gradle 9.4+), Kotlin 2.2.10 embutido no AGP, compileSdk 37.

## Gerar APK para distribuir (release assinado)

`./gradlew.bat assembleRelease --no-daemon` → `app/build/outputs/apk/release/app-release.apk` (~1,4 MB, R8, sem `debuggable`, sem permissão de internet). A assinatura vem de `release.keystore` + `keystore.properties` na raiz (ambos no `.gitignore`; a senha fica no `.properties`). **Faça backup dos dois fora do projeto**: com outra chave, ninguém consegue atualizar por cima do app já instalado. Sem esses arquivos o build de release sai sem assinatura e não instala.
Trocar entre debug e release no mesmo aparelho exige desinstalar (assinaturas diferentes). Verificar: `apksigner verify --verbose --print-certs` (rodar pelo PowerShell com `JAVA_HOME`; o `.bat` não aceita caminho estilo Git Bash).

## Segurança

Relatório e testes em `SEGURANCA.md`. Regras que não podem regredir: sem permissão de internet (o aviso e a política prometem isso); serviço só com `android:packageNames="com.instagram.android"` e vigilância (`vigia`) que esconde a barra se a janela ativa não for o Instagram; `Assinaturas.valida()` em todo arquivo/JSON externo (IDs só `com.instagram.android:id/`); só `MainActivity` exportada; mapeamento só em debug. Qualquer mudança nisso exige atualizar o aviso, a política e a declaração de Acessibilidade.

## Play Store

Pacote pronto em `playstore/` (AAB, ficha, gráficos, screenshots, política de privacidade, declaração de Acessibilidade, vídeo). Leia `playstore/LEIA-ME.md`. Antes de cada envio: aumentar `versionCode`, rodar `testDebugUnitTest bundleRelease lintRelease` e **atualizar a declaração de Acessibilidade e a política** se o app passar a ler/fazer algo novo (o texto lá descreve exatamente o código atual). O serviço declara só `typeWindowStateChanged|typeWindowContentChanged` e `flagReportViewIds`: não peça mais do que o código usa.

## Estrutura (`app/src/main/java/com/murilo/bloqueadorreels`)

- `ReelsBlockerService` — eventos, debounce 30 ms, overlay (fase 3), bloqueio de Reels (fase 4), modo de mapeamento (fase 1).
- `BarraReader` — `lerEstado` (fase 2). Sem certeza => `null` => não desenha.
- `Assinaturas` + `assets/assinaturas.json` — IDs/descrições (pt/en) dos botões. `filesDir/assinaturas.json` sobrescreve o asset (gancho do passo 7).
- `Prefs`, `MainActivity` (Compose: aviso/consentimento, status do serviço, interruptor, mapeamento).

## Regras

- Na dúvida, não desenhar overlay. Overlay sai sempre que o pacote ativo não é o Instagram.
- Sem permissão de internet no manifest (a tela de aviso promete que nada sai do celular). O passo 7 (JSON remoto) exige rever isso e o texto do aviso.
- Ícones próprios (`res/drawable/ic_nav_*`), nunca os do Instagram.
- Desvio da spec: `accessibility_config.xml` NÃO filtra `packageNames`, para saber quando o usuário sai do Instagram; o código ignora eventos de outros pacotes.
- Fase 1 confirmada no emulador (Instagram em inglês, 2026-11-25): `feed_tab`, `clips_tab`, `direct_tab` (desc "Message"), `search_tab`, `profile_tab` e `clips_viewer_view_pager` batem. Esta versão NÃO tem `creation_tab` na barra (o "+" fica no topo). Falta confirmar em português, modo escuro, conta comercial e outras versões.
- Cores do Instagram medidas por screenshot: fundo claro `#FFFFFF`, escuro `#0C1014` (não é preto). A barra substituta segue SEMPRE o tema do celular (sem opção manual); o modo escuro do app é outro interruptor (`Prefs.temaApp`) e só afeta a tela do app. Limitação conhecida: se o usuário forçar no Instagram um tema diferente do sistema, a barra ficará com a cor errada.
- O Instagram alterna entre estilos de barra (A/B). Em um deles o `isSelected` vem sempre falso; aí o serviço usa a última aba tocada na nossa barra (`abaLocal`, começa em "home"). Pode ficar defasado se o usuário voltar por outro caminho (ex.: botão voltar).
- Português testado no emulador (`adb shell cmd locale set-app-locales com.instagram.android --locales pt-BR`): descrições "Página inicial", "Mensagem", "Pesquisar e explorar", "Perfil" e "Reels" batem; clique e bloqueio OK.
- Links externos testados no emulador (`instagram://reels_home` e `https://www.instagram.com/reel/<codigo>/`, com o app aberto, em segundo plano e fechado): todos bloqueados com `BACK`, caindo no feed. `https://www.instagram.com/reels/` só abre o feed.
- Não deixe o modo de mapeamento ligado fora de coleta: despejar a árvore inteira é pesado e, junto de leituras a cada evento, travou o Instagram (ANR) no emulador. O serviço limita leituras a 1 a cada 250 ms.

## Comportamentos que valem manter (revisão de código, 2026-11-25)

- **Sem consentimento o serviço fica inerte** (`ativoParaInstagram()` exige `Prefs.consentimento`) e, ao conectar sem ele, abre o app no aviso. Testado: sem aceitar, a barra real e o Reels ficam intocados.
- **Troca de tela dentro do Instagram não esconde o overlay**: relê em 30 ms e só esconde se a barra sumiu (antes o ícone de Reels piscava). Janela de outro pacote (teclado, sistema, outro app) esconde e agenda releitura.
- **Bloqueio**: `BACK`; se o Reels continua aberto depois de 1,5 s, `HOME`; passados 4 s volta a ser `BACK` (`Decisoes`). Regras puras em `Decisoes.kt`, com teste.
- **Toque longo** na barra substituta vira `ACTION_LONG_CLICK` no botão real (ex.: seletor de contas no Perfil).
- **Aviso de saúde**: se o Instagram é usado por mais de 5 min sem reconhecer barra nem tela de Reels, a tela do app mostra "Barra do Instagram não reconhecida" (versão nova do Instagram). Gravado em `Prefs` a cada 30 s no máximo.
- **Mapeamento só em build debug** (`Prefs.ehDebug`); no release o cartão some e o serviço ignora a preferência.
- **Barra vertical** se o container for mais alto que largo (trilho lateral). Só lógica, nunca vista num aparelho assim.
- Testado sem problema: teclado aberto (o overlay não cobre o teclado) e folha de comentários (o overlay não aparece por cima).

## Testar no emulador

AVD `Medium_Phone_API_37.0` (Play Store). `adb` em `%LOCALAPPDATA%\Android\Sdk\platform-tools`. Ativar o serviço sem a UI: `adb shell settings put secure enabled_accessibility_services com.murilo.bloqueadorreels/com.murilo.bloqueadorreels.ReelsBlockerService`. Atalho para Reels: `adb shell am start -a android.intent.action.VIEW -d instagram://reels_home -p com.instagram.android`; o log do bloqueio sai em `adb logcat -s ReelsBlocker`. No Git Bash use `MSYS_NO_PATHCONV=1` e `adb exec-out screencap -p > arq.png` (PowerShell corrompe o PNG).

## Textos legais (política de privacidade e termos de uso)

Fonte única: `app/src/main/assets/politica-de-privacidade.md` e `termos-de-uso.md` (um bloco por linha: `# `, `## `, `- `, `**negrito**`, tokens `{{RESPONSAVEL}}` e `{{CONTATO}}`). O app os mostra em tela (`DocumentoLegal`/`TelaDocumento`, link no rodapé do aviso e da tela principal; leitura/parse em `Documento.kt`). Os `.html` (site do GitHub Pages, pasta `docs/`) saem de `playstore/gerar-html.ps1` a partir desses mesmos arquivos: nunca edite o HTML à mão. Depois de gerar, `git push` publica. Site: https://murilsanches.github.io/bloqueador-reels/ `res/values/strings.xml` tem `responsavel_nome` ("Murilo Studios") e `contato_email` (preenchidos; se ficarem vazios o app diz "pela página do app na Google Play"). Ao mudar, rode `gerar-html.ps1` com os mesmos valores. `DocumentoTest` falha se faltar seção obrigatória, token ou se sobrar placeholder. Mudou o que o app lê/faz? Atualize política, termos, aviso (`TelaAviso`), declaração de Acessibilidade e ficha juntos. Os termos são modelo geral, não revisão jurídica.

## Publicação automática e licença

- **Licença:** MIT para o código (`LICENSE`). Nome, ícone e identidade visual não entram na licença (dito nos Termos de uso, seção 4).
- **Release por push:** `.github/workflows/release.yml` — push na `main` (que mexa no app) roda testes + lint, gera o AAB assinado, cria um GitHub Release `v0.1.N` e, com `PLAY_SERVICE_ACCOUNT_JSON` configurado, envia para a faixa `PLAY_TRACK` (padrão `internal`). Passos, Secrets e como passar para produção: `playstore/PUBLICACAO-AUTOMATICA.md`. `[skip ci]` no commit pula. `versionCode`/`versionName` vêm de `-PversionCode`/`-PversionName` (local: 1 / 0.1.0). Não commite `release.keystore`/`keystore.properties`; o CI os recria dos Secrets.
