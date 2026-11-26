# App Android: Bloqueador de Reels do Instagram

Nov 25, 2026 · @Murilo

## Visão geral

O app esconde o botão de Reels do Instagram no Android. Ele desenha uma barra de navegação própria por cima da barra real, sem o botão de Reels, e repassa os toques para os botões verdadeiros. Também volta automaticamente se a tela de Reels abrir por outro caminho.

Tudo é feito com um **Serviço de Acessibilidade** (`AccessibilityService`). Esse serviço lê a tela do Instagram e desenha overlays do tipo `TYPE_ACCESSIBILITY_OVERLAY`, que não precisam da permissão extra de "sobrepor a outros apps".

Limites da plataforma:

- **Android:** viável. É a plataforma-alvo deste projeto.
- **iPhone:** não dá. O iOS não deixa um app ler nem cobrir a tela de outro. O Tempo de Uso só bloqueia o app inteiro.
- **Navegador:** se quiser uma versão extra, uma extensão resolve com CSS: `a[href="/reels/"] { display: none !important; }`.
- **Evitar:** APKs modificados do Instagram. Eles violam os termos de uso e podem levar ao banimento da conta.

## Arquitetura

&#91;embedded content: arquitetura · 5 componentes\]

O Serviço de Acessibilidade é o centro do app. Ele recebe os eventos do Instagram, consulta as assinaturas e decide entre desenhar a barra substituta ou voltar da tela de Reels. Os toques na barra substituta voltam para o Instagram como cliques nos botões reais.

## Tela de configurações do Instagram

Ao abrir o app, o usuário vê uma seção "Instagram" com um interruptor para ativar ou desativar o bloqueio da aba Reels. O serviço lê essa preferência e reage na hora, sem reiniciar nada.

O que a tela mostra:

- **Status do serviço:** "Ativo" ou "Desativado". Se estiver desativado, um botão leva para Configurações › Acessibilidade.
- **Instagram › Ocultar aba Reels:** liga/desliga a barra substituta (fase 3) e o bloqueio da tela de Reels (fase 4).
- Organize a tela por app (uma seção "Instagram"), para caber outras funções e outros apps depois.

Preferência salva com `SharedPreferences` (ou DataStore):

```kotlin
object Prefs {
    const val ARQUIVO = "config"
    const val IG_OCULTAR_REELS = "ig_ocultar_reels"

    fun prefs(ctx: Context) = ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
    fun ocultarReels(ctx: Context) = prefs(ctx).getBoolean(IG_OCULTAR_REELS, true)
    fun setOcultarReels(ctx: Context, valor: Boolean) =
        prefs(ctx).edit().putBoolean(IG_OCULTAR_REELS, valor).apply()
}
```

O interruptor na tela (Jetpack Compose):

```kotlin
@Composable
fun ConfigInstagram(ctx: Context) {
    var ativo by remember { mutableStateOf(Prefs.ocultarReels(ctx)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Ocultar aba Reels", Modifier.weight(1f))
        Switch(checked = ativo, onCheckedChange = {
            ativo = it
            Prefs.setOcultarReels(ctx, it)
        })
    }
}
```

No serviço, escute a mudança para remover ou redesenhar a barra imediatamente:

```kotlin
private val ouvinte = SharedPreferences.OnSharedPreferenceChangeListener { _, chave ->
    if (chave == Prefs.IG_OCULTAR_REELS) {
        ultimoEstado = null          // força reavaliar
        if (!Prefs.ocultarReels(this)) esconderOverlay() else verificarBarra()
    }
}

override fun onServiceConnected() {
    Prefs.prefs(this).registerOnSharedPreferenceChangeListener(ouvinte)
}
```

E no início de `verificarBarra`, saia cedo se a opção estiver desligada: `if (!Prefs.ocultarReels(this)) return esconderOverlay()`.

Para saber se o serviço está ativo, compare o nome do serviço com `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` toda vez que a tela voltar ao primeiro plano (`onResume`).

## Configuração do Serviço de Acessibilidade

O serviço é declarado no manifest e configurado num XML. Ele escuta só o pacote do Instagram.

`AndroidManifest.xml`:

```xml
<service
    android:name=".ReelsBlockerService"
    android:exported="false"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_config" />
</service>
```

`res/xml/accessibility_config.xml`:

```xml
<accessibility-service
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewSelected|typeViewScrolled"
    android:packageNames="com.instagram.android"
    android:canRetrieveWindowContent="true"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
    android:notificationTimeout="50"
    android:description="@string/accessibility_description" />
```

- `canRetrieveWindowContent` permite ler a árvore de views.
- `flagReportViewIds` faz os IDs das views aparecerem (`viewIdResourceName`).
- `notificationTimeout` de 50 ms agrupa eventos muito próximos.
- O usuário ativa o serviço em Configurações › Acessibilidade. O app deve ter uma tela que leva até lá com `Settings.ACTION_ACCESSIBILITY_SETTINGS`.

## Fase 1: mapear as variações da barra

A barra do Instagram muda conforme a versão do app, o idioma, testes A/B e o tipo de conta. A ordem dos botões pode mudar, e "Loja" ou "Mensagens" podem entrar no lugar de algum. Antes de desenhar qualquer coisa, colete essas variações.

Crie um **modo de mapeamento** só para desenvolvimento, que despeja a árvore de views no Logcat:

```kotlin
private fun despejarArvore(node: AccessibilityNodeInfo?, nivel: Int = 0) {
    node ?: return
    val r = Rect().also { node.getBoundsInScreen(it) }
    Log.d("MAPA", "  ".repeat(nivel) +
        "id=${node.viewIdResourceName} desc=${node.contentDescription} " +
        "texto=${node.text} sel=${node.isSelected} vis=${node.isVisibleToUser} $r")
    for (i in 0 until node.childCount) despejarArvore(node.getChild(i), nivel + 1)
}
```

Alternativas para inspecionar: o *Layout Inspector* do Android Studio, ou `adb shell uiautomator dump` com a tela aberta.

Situações para mapear:

- [ ] Feed, pesquisa, perfil e Reels abertos
- [ ] Modo claro e modo escuro
- [ ] Celular em português e em inglês
- [ ] Conta pessoal e conta comercial/criador
- [ ] Pelo menos duas versões do Instagram
- [ ] Telas sem barra: stories, DMs, câmera, teclado aberto, vídeo em tela cheia

Com os logs, monte uma tabela de **assinaturas** que o app usa para reconhecer cada botão:

```kotlin
data class AssinaturaBotao(
    val chave: String,             // "home", "search", "create", "reels", "profile"
    val ids: List<String>,         // IDs possíveis
    val descricoes: List<String>   // "Início", "Home", "Página inicial"...
)
```

Buscar por descrição de conteúdo costuma ser mais estável que por ID. Guarde as assinaturas num JSON, de preferência baixado de um servidor seu, para corrigir sem publicar outra versão do app.

## Fase 2: monitorar a barra em tempo real

A cada evento de tela, o serviço tira um "retrato" da barra e só mexe no overlay se algo mudou. Os eventos chegam em rajadas, então um debounce de 30 ms evita trabalho repetido.

```kotlin
data class EstadoBarra(
    val visivel: Boolean,
    val bounds: Rect,
    val botoes: List<String>,      // ordem atual, ex: [home, search, create, reels, profile]
    val selecionado: String?,      // aba ativa
    val modoEscuro: Boolean
)

private var ultimoEstado: EstadoBarra? = null
private val handler = Handler(Looper.getMainLooper())
private val atualizar = Runnable { verificarBarra() }

override fun onAccessibilityEvent(event: AccessibilityEvent) {
    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
        esconderOverlay() // troca de tela: esconde já, reaparece ao confirmar
    }
    handler.removeCallbacks(atualizar)
    handler.postDelayed(atualizar, 30)
}

private fun verificarBarra() {
    val root = rootInActiveWindow ?: return esconderOverlay()
    val estado = lerEstado(root)   // usa as assinaturas da fase 1
    if (estado == ultimoEstado) return
    ultimoEstado = estado
    if (!estado.visivel) esconderOverlay() else desenharBarra(estado)
}
```

O que `lerEstado` verifica:

| Campo | Como ler |
| --- | --- |
| Visível | Container da barra existe, `isVisibleToUser` e altura maior que zero |
| Posição | `getBoundsInScreen()` do container |
| Ordem dos botões | Botões reconhecidos, ordenados pelo X dos `bounds` |
| Aba ativa | Botão com `isSelected == true` |
| Modo escuro | `resources.configuration.uiMode` (o Instagram segue o tema do sistema por padrão) |

Desempenho: depois de achar o container da barra, busque só dentro dele, sem percorrer a árvore inteira a cada evento.

## Fase 3: barra substituta e repasse de cliques

O overlay cobre a barra inteira, com a mesma cor de fundo, e mostra só os botões desejados distribuídos por igual. Assim não fica buraco onde estava o Reels. Cada toque chama `ACTION_CLICK` no botão real, que continua embaixo.

```kotlin
private val botoesReais = mutableMapOf<String, AccessibilityNodeInfo>()

private fun criarBarra(estado: EstadoBarra): View {
    val corFundo = if (estado.modoEscuro) Color.BLACK else Color.WHITE
    val barra = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(corFundo)
    }
    estado.botoes.filter { it != "reels" }.forEach { chave ->
        barra.addView(ImageButton(this).apply {
            setImageResource(iconePara(chave))   // ícones próprios (Material Icons)
            isSelected = chave == estado.selecionado
            background = null
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
            setOnClickListener {
                botoesReais[chave]?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        })
    }
    return barra
}

private fun desenharBarra(estado: EstadoBarra) {
    val wm = getSystemService(WINDOW_SERVICE) as WindowManager
    val r = estado.bounds
    val params = WindowManager.LayoutParams(
        r.width(), r.height(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START; x = r.left; y = r.top }

    overlay?.let { wm.removeView(it) }
    overlay = criarBarra(estado).also { wm.addView(it, params) }
}

private fun esconderOverlay() {
    overlay?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
    overlay = null
}
```

Cuidados:

- **Aba ativa:** destaque o botão equivalente ao que o Instagram marca como `isSelected`.
- **Ícones:** use ícones próprios, não os do Instagram, para evitar problema de marca.
- **Sair do Instagram:** remova o overlay sempre, senão ele fica por cima de outros apps.
- **Nós antigos:** os `AccessibilityNodeInfo` ficam desatualizados. Atualize `botoesReais` a cada leitura de estado e, se `performAction` retornar `false`, releia a barra.

## Fase 4: bloquear a tela de Reels por outros caminhos

Mesmo sem o botão, os Reels abrem por vídeos no feed, na pesquisa, em perfis e nas DMs. O serviço detecta a tela do visualizador de Reels e volta na hora.

```kotlin
private fun bloquearSeForReels(root: AccessibilityNodeInfo) {
    val naTelaDeReels = assinaturasReels.any { id ->
        root.findAccessibilityNodeInfosByViewId(id).isNotEmpty()
    }
    if (naTelaDeReels) performGlobalAction(GLOBAL_ACTION_BACK)
}
```

- Um ID conhecido do visualizador é `com.instagram.android:id/clips_viewer_view_pager`. Confirme na fase 1, porque ele muda com as atualizações.
- Chame `bloquearSeForReels` dentro de `verificarBarra`, antes de desenhar a barra.
- Se o "voltar" cair de novo nos Reels, use `GLOBAL_ACTION_HOME` como alternativa.
- Opcional: mostre um aviso curto ("Reels bloqueado") para o usuário entender o que aconteceu.

## Pontos de atenção e plano B

A regra principal: se o app não tiver certeza do que está na tela, ele não desenha nada. Melhor o Instagram ficar normal do que quebrado.

| Problema | Solução |
| --- | --- |
| Barra real aparece por um instante na troca de tela (atraso de 30 a 100 ms) | Esconder o overlay no `typeWindowStateChanged` e mostrar só ao confirmar o estado |
| Barra some em stories, DMs, câmera, teclado, tela cheia | Checar `isVisibleToUser` a cada evento e esconder o overlay junto |
| Instagram atualiza e muda IDs ou textos | Assinaturas em JSON remoto; preferir descrição de conteúdo a ID |
| Idioma do celular muda o texto ("Início" ou "Home") | Listar as descrições nos dois idiomas na assinatura |
| Nenhuma assinatura bate | Não desenhar a barra; manter só o bloqueio da fase 4 |
| Rotação, tema, tamanho de fonte | Recalcular `bounds` e cor a cada mudança de estado |
| Overlay preso em outro app | Remover sempre que o pacote ativo não for o Instagram |
| Consumo de bateria | Debounce, busca só dentro do container, serviço restrito ao pacote do Instagram |

## Publicação e roteiro

O Google revisa com rigor apps que usam acessibilidade. Apps de bem-estar digital costumam ser aprovados quando são transparentes.

Para a Play Store:

- Mostrar uma tela de aviso antes de mandar o usuário ativar o serviço. Ela explica o que o app lê, por que lê e que nada sai do celular.
- Pedir consentimento explícito, com um botão de aceite.
- Preencher a declaração de uso da API de acessibilidade no Play Console, de preferência com um vídeo mostrando o fluxo.
- Ter uma política de privacidade publicada.

Roteiro sugerido:

1. Criar o projeto (Kotlin, minSdk 26) com o serviço e a tela de ativação.
2. Implementar o modo de mapeamento e coletar as variações da barra.
3. Montar as assinaturas e o leitor de estado (`lerEstado`).
4. Fazer o bloqueio da tela de Reels (fase 4). Esse já é um app funcional.
5. Adicionar a barra substituta com repasse de cliques (fase 3).
6. Tratar transições, tema e telas sem barra.
7. Mover as assinaturas para um JSON remoto.
8. Testar em vários aparelhos e versões do Instagram e publicar.
