# Declaração de uso da API de Acessibilidade (Play Console)

Local: **Política › Conteúdo do app › Declaração de permissões e APIs sensíveis › API de Acessibilidade**.
Marque que o app **não** é uma ferramenta de acessibilidade (o manifesto já declara `isAccessibilityTool="false"`).

> Os textos abaixo são fiéis ao que o código faz hoje (v0.1.0). Se o código mudar, atualize a declaração antes de publicar a nova versão: o Google exige reenviar o formulário.

## 1. Qual a funcionalidade principal do app?

Bloquear o Reels no app do Instagram por escolha do usuário (bem-estar digital): esconder o botão de Reels da barra inferior e voltar automaticamente se a tela de Reels abrir por outro caminho. Por escolha do usuário (opções desligadas por padrão), deixa ver o Reels aberto por um link de outro app ou enviado numa conversa.

## 2. Por que o app precisa da API de Acessibilidade?

O Android não oferece outra forma de (a) saber o que está na tela de outro app, (b) desenhar sobre a barra de outro app e (c) voltar quando outra tela abre. Alternativas avaliadas e por que não servem:

| Alternativa | Por que não resolve |
|---|---|
| Tempo de uso / bem-estar digital do Android | Só bloqueia o app inteiro, não uma aba |
| Permissão "sobrepor a outros apps" | Desenha, mas não enxerga a tela nem toca nos botões |
| Apps modificados do Instagram | Violam os termos do Instagram e podem banir a conta |

## 3. O que o app lê e faz com a API (técnico)

| Recurso | Uso |
|---|---|
| `canRetrieveWindowContent` + `flagReportViewIds` | Limitação honesta: o Android não restringe a leitura de conteúdo por pacote, só os eventos; a restrição de leitura ao Instagram é feita pelo código (verificação do pacote antes de qualquer leitura). Só no pacote `com.instagram.android`: lê IDs e descrições dos botões da barra inferior, lê o número de mensagens não lidas do botão de mensagens (somente dígitos, validados; só para exibir na barra substituta, nunca guardado) e verifica se o visualizador de Reels está visível. Com a opção "Permitir Reels do chat" ligada, verifica também se o visualizador traz o elemento "enviado por" (apenas a presença do ID de view; nome e texto não são lidos). Não lê textos de posts, mensagens, nomes, senhas nem texto digitado. |
| Eventos `typeWindowStateChanged` e `typeWindowContentChanged`, com `android:packageNames="com.instagram.android"` | Saber quando a tela do Instagram mudou. O próprio Android só entrega ao serviço eventos do Instagram (testado: nenhum evento de launcher, Chrome ou sistema chega). |
| Vigilância do overlay (**enquanto a barra substituta está na tela e, só com a opção "Permitir Reels de links" ligada, também enquanto o Instagram está na frente**, para saber quando ele volta vindo de outro app) e `flagRetrieveInteractiveWindows` | Várias vezes por segundo consulta só metadados das janelas (identificador da janela ativa). Quando a janela ativa muda, e também a cada 0,6 s, lê apenas o nome do pacote da janela ativa, para remover a barra se o usuário sair do Instagram (outro app, diálogo do sistema, tela de bloqueio). Não lê título nem conteúdo dessas janelas. |
| Overlay `TYPE_ACCESSIBILITY_OVERLAY` | Desenha a barra substituta sobre a barra do Instagram. |
| `performAction(CLICK / LONG_CLICK)` | Só quando o **usuário toca** na barra substituta: repassa o toque ao botão real. |
| `performGlobalAction(BACK / HOME)` | Quando a tela de Reels abre: volta (e usa início só se o "voltar" não resolver), exceto nos Reels que o usuário permitiu pelas opções de link e de chat. |

Dados: nada é gravado além de preferências do próprio app (interruptor, tema, opções de Reels de links e do chat, aceite, horários internos de diagnóstico). Nada é enviado: o app **não tem a permissão de internet**.

## 4. Divulgação em destaque e consentimento (dentro do app)

- Tela "Antes de ativar", exibida antes de qualquer ativação, separada de outros avisos: explica o que lê, por quê, para onde vai e como controlar.
- Dois botões: **"Entendi e aceito"** e **"Não aceito"**. Recusar fecha o app e nada é ativado; o aviso volta na próxima abertura.
- Sem o aceite, o serviço fica inerte mesmo se o usuário o ativar direto em Configurações (e o app abre o aviso).
- A descrição do serviço nas Configurações do Android é complementar, não substitui o aviso.

## 5. O app age de forma autônoma?

Não é um agente nem planeja tarefas. As ações são fixas e escolhidas pelo usuário ao ligar o interruptor: repassar um toque que o usuário deu e voltar quando a tela de Reels abre (que é o bloqueio que ele pediu). O usuário desliga com o interruptor do app ou nas Configurações de Acessibilidade.

## 6. Vídeo exigido

- **`video-consentimento.mp4`** (56 s, já gravado neste projeto): aviso, política de privacidade aberta pelo aviso, caminho de recusa, aceite, abertura das Configurações de Acessibilidade e ativação do serviço.
- Falta mostrar a função em uso. Há um rascunho em `video-funcionamento-LOCAL-feed-real.mp4`, mas ele mostra um feed real e a conta do emulador: **revise/regrave** com uma conta de teste antes de subir. Pode ser emendado ao primeiro.
- Suba no YouTube como **Não listado** e cole o link no formulário.

Roteiro caso queira regravar no seu aparelho (≤ 2 min):
1. Abrir o app pela primeira vez → mostrar o aviso inteiro (rolar).
2. Tocar em **Não aceito** → app fecha. Abrir de novo.
3. Tocar em **Entendi e aceito** → **Abrir Acessibilidade** → ativar o serviço → **Permitir**.
4. Voltar ao app: "Serviço ativo".
5. Abrir o Instagram: barra sem Reels; tocar em Início/Pesquisar.
6. Abrir um link de Reels (ex.: mandar para si mesmo) → o app volta e mostra "Reels bloqueado".
7. Desligar o interruptor no app → o Reels volta a aparecer.
