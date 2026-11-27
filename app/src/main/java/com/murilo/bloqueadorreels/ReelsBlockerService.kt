package com.murilo.bloqueadorreels

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class ReelsBlockerService : AccessibilityService() {

    companion object {
        const val PKG_INSTAGRAM = "com.instagram.android"
        private const val TAG = "ReelsBlocker"
        private const val DEBOUNCE_MS = 30L
        private const val INTERVALO_MIN_MS = 250L
        private const val VIGIA_MS = 80L          // consulta leve (só metadados de janelas)
        private const val VIGIA_PACOTE_MS = 600L  // confirmação periódica pelo pacote da janela ativa
        private const val ABA_PENDENTE_MS = 500L
        private const val AVISO_INTERVALO_MS = 3000L
        private const val MAPA_INTERVALO_MS = 1500L
        private const val GRAVAR_SAUDE_A_CADA_MS = 30_000L
        private val COR_FUNDO_ESCURO = Color.parseColor("#0C1014")
    }

    private val handler = Handler(Looper.getMainLooper())
    private val atualizar = Runnable { verificarBarra() }

    private lateinit var assinaturas: Assinaturas
    private var overlay: ViewGroup? = null
    private var estadoDesenhado: EstadoBarra? = null
    private var ultimaJanelaAtivaId = -1   // janela ativa na última confirmação de pacote da vigia
    private var ultimaConfirmacaoPacote = 0L
    private var abaPendente: String? = null // aba tocada na nossa barra, até o Instagram confirmar
    private var abaPendenteAte = 0L
    private var ultimoEstado: EstadoBarra? = null
    private val botoesReais = mutableMapOf<String, AccessibilityNodeInfo>()

    private var ultimoBloqueio = 0L
    private var ultimoAviso = 0L
    private var ultimoMapa = 0L
    private var ultimaVerificacao = 0L
    private var abaLocal = "home"


    // Saúde: avisa o usuário se o Instagram é usado por muito tempo sem reconhecer nada.
    private var semReconhecerDesde = 0L
    private var ultimoUsoInstagram = 0L
    private var ultimaGravacaoSaude = 0L

    private val ouvinte = SharedPreferences.OnSharedPreferenceChangeListener { _, chave ->
        if (chave == Prefs.IG_OCULTAR_REELS || chave == Prefs.CONSENTIMENTO) {
            ultimoEstado = null // força reavaliar
            if (!ativoParaInstagram()) esconderOverlay() else verificarBarra()
        }
    }

    /** O serviço só age com o aviso aceito no app e o interruptor ligado. */
    private fun ativoParaInstagram() = Prefs.consentimento(this) && Prefs.ocultarReels(this)

    override fun onServiceConnected() {
        assinaturas = Assinaturas.carregar(this)
        Prefs.prefs(this).registerOnSharedPreferenceChangeListener(ouvinte)
        semReconhecerDesde = Prefs.semReconhecerDesde(this)
        ultimoUsoInstagram = Prefs.ultimoUsoInstagram(this)
        // Ativado direto em Acessibilidade sem passar pelo aviso: leva o usuário até ele.
        if (!Prefs.consentimento(this)) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onInterrupt() {
        esconderOverlay()
    }

    /** Serviço desativado: só remove o overlay e para tudo (a barra original volta como era). */
    private fun desligar() {
        Prefs.prefs(this).unregisterOnSharedPreferenceChangeListener(ouvinte)
        handler.removeCallbacksAndMessages(null)
        esconderOverlay()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        desligar()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        desligar()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pacote = event.packageName?.toString()
        if (pacote == packageName) return // eventos do nosso próprio overlay

        val janelaMudou = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        if (pacote != PKG_INSTAGRAM) {
            // O sistema já filtra por pacote (accessibility_config.xml); isto é só uma segunda
            // barreira: nada de outro app é lido, e o overlay sai junto.
            if (janelaMudou) esconderOverlay()
            return
        }
        if (!ativoParaInstagram()) {
            esconderOverlay()
            return
        }
        // Troca de tela dentro do Instagram: NÃO esconde o overlay (a barra real, com o Reels,
        // apareceria por um instante). Relê logo; se a barra sumiu, a leitura esconde.
        // Debounce + intervalo mínimo nos demais eventos: no Reels eles não param e cada leitura
        // da árvore custa IPC na thread principal do Instagram (chegou a gerar ANR).
        val espera = if (janelaMudou) DEBOUNCE_MS
        else maxOf(DEBOUNCE_MS, ultimaVerificacao + INTERVALO_MIN_MS - SystemClock.uptimeMillis())
        agendar(espera)
    }

    private fun agendar(espera: Long) {
        handler.removeCallbacks(atualizar)
        handler.postDelayed(atualizar, espera)
    }

    private fun verificarBarra() {
        ultimaVerificacao = SystemClock.uptimeMillis()
        if (!ativoParaInstagram()) return esconderOverlay()
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != PKG_INSTAGRAM) return esconderOverlay()

        if (Prefs.ehDebug(this) && Prefs.mapeamento(this)) despejarMapa(root)

        // Fase 4: tela de Reels aberta por qualquer caminho -> volta.
        if (bloquearSeForReels(root)) {
            registrarSaude(reconheceu = true)
            return esconderOverlay()
        }

        // Fase 2: lê a barra. Sem certeza -> não desenha nada.
        val leitura = BarraReader.ler(root, assinaturas, modoEscuro())
        registrarSaude(reconheceu = leitura != null)
        if (leitura == null) return esconderOverlay()

        botoesReais.clear()
        botoesReais.putAll(leitura.botoesReais) // nós antigos ficam velhos: atualiza a cada leitura

        // Algumas versões do Instagram não marcam a aba ativa (isSelected sempre falso).
        // Nesse caso usamos a última aba tocada na nossa barra (começa em "home").
        val lido = leitura.estado
        // Toque recente: segura a aba tocada até o Instagram marcá-la (ou ~500 ms), para um evento
        // que chegue antes da troca não devolver o destaque para a aba antiga.
        val pendente = abaPendente?.takeIf {
            SystemClock.uptimeMillis() < abaPendenteAte && lido.selecionado != it
        }
        if (pendente == null) abaPendente = null
        val ativa = pendente ?: Decisoes.abaAtiva(lido.selecionado, abaLocal)
        abaLocal = ativa
        val estado = lido.copy(selecionado = ativa)
        if (estado == ultimoEstado) return
        ultimoEstado = estado
        if (!estado.visivel) removerOverlay() else desenharBarra(estado)
    }

    // ---------- Saúde ----------

    private fun registrarSaude(reconheceu: Boolean) {
        val agora = System.currentTimeMillis()
        semReconhecerDesde = Decisoes.semReconhecerDesde(semReconhecerDesde, reconheceu, agora, ultimoUsoInstagram)
        ultimoUsoInstagram = agora
        if (agora - ultimaGravacaoSaude > GRAVAR_SAUDE_A_CADA_MS) {
            ultimaGravacaoSaude = agora
            Prefs.gravarSaude(this, semReconhecerDesde, ultimoUsoInstagram)
        }
    }

    // ---------- Fase 4 ----------

    private fun bloquearSeForReels(root: AccessibilityNodeInfo): Boolean {
        val idReels = assinaturas.reelsViewerIds.firstOrNull { id ->
            root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser }
        } ?: return false

        val agora = SystemClock.uptimeMillis()
        val acao = Decisoes.acaoReels(agora - ultimoBloqueio)
        if (acao == AcaoReels.IGNORAR) return true // "voltar" ainda em andamento
        ultimoBloqueio = agora
        Log.i(TAG, "tela de Reels ($idReels): $acao")
        performGlobalAction(if (acao == AcaoReels.HOME) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)

        if (agora - ultimoAviso > AVISO_INTERVALO_MS) {
            ultimoAviso = agora
            Toast.makeText(this, R.string.reels_bloqueado, Toast.LENGTH_SHORT).show()
        }
        return true
    }

    // ---------- Fase 3 ----------

    /** A barra substituta sempre acompanha o tema do celular (o Instagram segue o sistema por padrão). */
    private fun modoEscuro() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Estilo da barra do Instagram: contorno quando inativo, preenchido quando ativo. */
    private fun iconePara(chave: String, ativo: Boolean) = when (chave) {
        "home" -> if (ativo) R.drawable.ic_nav_home_on else R.drawable.ic_nav_home
        "search" -> if (ativo) R.drawable.ic_nav_search_on else R.drawable.ic_nav_search
        "create" -> if (ativo) R.drawable.ic_nav_create_on else R.drawable.ic_nav_create
        "shop" -> if (ativo) R.drawable.ic_nav_shop_on else R.drawable.ic_nav_shop
        "messages" -> if (ativo) R.drawable.ic_nav_messages_on else R.drawable.ic_nav_messages
        else -> if (ativo) R.drawable.ic_nav_profile_on else R.drawable.ic_nav_profile
    }

    private fun dp(valor: Float) = (valor * resources.displayMetrics.density).toInt()

    /**
     * Aviso de mensagens não lidas, no canto do ícone (como no Instagram): bolinha vermelha, ou
     * bolinha com o número quando ele foi lido.
     */
    private fun criarBadge(texto: String): View {
        val fundo = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10f).toFloat()
            setColor(Color.parseColor("#FF3040"))
        }
        if (texto.isEmpty()) {
            // Medido no Instagram: ~6dp, a ~11dp à direita e ~7dp abaixo do centro do ícone.
            return View(this).apply {
                background = fundo.apply { cornerRadius = dp(4f).toFloat() }
                layoutParams = FrameLayout.LayoutParams(dp(6.5f), dp(6.5f), Gravity.CENTER)
                translationX = dp(11.5f).toFloat()
                translationY = dp(7f).toFloat()
            }
        }
        return TextView(this).apply {
            this.text = texto
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = fundo
            minWidth = dp(16f)
            minHeight = dp(16f)
            setPadding(dp(4f), 0, dp(4f), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            )
            translationX = dp(13f).toFloat()
            translationY = dp(5f).toFloat()
        }
    }

    /** Pílula atrás do ícone ativo (cor medida no Instagram: claro #F0F2F5, escuro #25282D). */
    private fun criarPilula(escuro: Boolean): View = View(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20f).toFloat()
            setColor(Color.parseColor(if (escuro) "#25282D" else "#F0F2F5"))
        }
        layoutParams = FrameLayout.LayoutParams(dp(72f), dp(40f), Gravity.CENTER)
    }

    private fun descricaoPara(chave: String) = getString(
        when (chave) {
            "home" -> R.string.desc_home
            "search" -> R.string.desc_search
            "create" -> R.string.desc_create
            "shop" -> R.string.desc_shop
            "messages" -> R.string.desc_messages
            else -> R.string.desc_profile
        }
    )

    private fun ehVertical(estado: EstadoBarra) = estado.bounds.height() > estado.bounds.width()

    private fun criarBarra(estado: EstadoBarra): ViewGroup {
        // Fundo do Instagram medido no emulador: claro #FFFFFF, escuro #0C1014 (não é preto puro).
        val corFundo = if (estado.modoEscuro) COR_FUNDO_ESCURO else Color.WHITE
        // Container alto e estreito (trilho lateral em tablet/dobrável): empilha na vertical.
        val vertical = ehVertical(estado)
        val barra = LinearLayout(this).apply {
            orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(corFundo)
        }
        botoesVisiveis(estado).forEach { chave ->
            val espaco = FrameLayout(this).apply {
                layoutParams = if (vertical) LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
                else LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
            }
            preencherEspaco(espaco, chave, estado)
            barra.addView(espaco)
        }
        return barra
    }

    private fun botoesVisiveis(estado: EstadoBarra) = estado.botoes.filter { it != "reels" }

    /** Conteúdo de um botão (pílula, ícone, aviso). Refeito só nos botões que mudaram. */
    private fun preencherEspaco(espaco: FrameLayout, chave: String, estado: EstadoBarra) {
        // No Instagram todos os ícones têm a mesma cor (preto no claro, branco no escuro);
        // o que muda entre ativo e inativo é contorno x preenchido.
        val corIcone = if (estado.modoEscuro) Color.WHITE else Color.BLACK
        val ativo = chave == estado.selecionado
        val vertical = ehVertical(estado)
        espaco.removeAllViews()
        val botao = ImageButton(this).apply {
            setImageResource(iconePara(chave, ativo)) // desenhos próprios no mesmo estilo
            scaleType = ImageView.ScaleType.CENTER     // 24dp, como no Instagram
            // Sem foco de teclado/D-pad: o destaque de foco padrão pintava um retângulo cinza.
            isFocusable = false
            isFocusableInTouchMode = false
            defaultFocusHighlightEnabled = false
            imageTintList = ColorStateList.valueOf(corIcone)
            isSelected = ativo
            background = null
            contentDescription = descricaoPara(chave)
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setOnClickListener { acionarBotaoReal(chave, AccessibilityNodeInfo.ACTION_CLICK) }
            // Toque longo (ex.: trocar de conta no Perfil) também chega ao botão real.
            setOnLongClickListener {
                acionarBotaoReal(chave, AccessibilityNodeInfo.ACTION_LONG_CLICK)
                true
            }
        }
        if (ativo && !vertical) espaco.addView(criarPilula(estado.modoEscuro))
        espaco.addView(botao)
        estado.badges[chave]?.let { espaco.addView(criarBadge(it)) }
    }

    /** Mesma janela serve se só mudaram aba ativa e avisos: evita remover/recriar o overlay (piscava). */
    private fun mesmaEstrutura(a: EstadoBarra, b: EstadoBarra) =
        a.bounds == b.bounds && a.modoEscuro == b.modoEscuro &&
            botoesVisiveis(a) == botoesVisiveis(b)

    /** Atualiza no lugar só os botões cuja aba ativa ou aviso mudou. */
    private fun atualizarBarra(novo: EstadoBarra) {
        val barra = overlay ?: return
        val antigo = estadoDesenhado ?: return
        botoesVisiveis(novo).forEachIndexed { i, chave ->
            val mudou = (chave == antigo.selecionado) != (chave == novo.selecionado) ||
                antigo.badges[chave] != novo.badges[chave]
            if (mudou) (barra.getChildAt(i) as? FrameLayout)?.let { preencherEspaco(it, chave, novo) }
        }
        estadoDesenhado = novo
    }

    private fun acionarBotaoReal(chave: String, acao: Int) {
        val no = botoesReais[chave]
        val ok = no != null && executar(no, acao)
        // "Criar" abre o compositor, não vira aba: não ganha destaque.
        if (ok && acao == AccessibilityNodeInfo.ACTION_CLICK && chave != "create") {
            abaLocal = chave
            abaPendente = chave
            abaPendenteAte = SystemClock.uptimeMillis() + ABA_PENDENTE_MS
            // Destaque imediato, sem recriar a janela: a releitura abaixo só confirma.
            val atual = estadoDesenhado
            if (overlay != null && atual != null) {
                val novo = atual.copy(selecionado = chave)
                atualizarBarra(novo)
                ultimoEstado = novo
            }
        } else if (!ok) {
            ultimoEstado = null // nó velho/não clicável: relê a barra
        }
        handler.removeCallbacks(atualizar)
        handler.postDelayed(atualizar, if (ok) 150L else 0L)
    }

    /** Executa a ação no nó ou no ancestral mais próximo que a aceite. */
    private fun executar(no: AccessibilityNodeInfo, acao: Int): Boolean {
        var atual: AccessibilityNodeInfo? = no
        var subidas = 0
        while (atual != null && subidas < 4) {
            val aceita = if (acao == AccessibilityNodeInfo.ACTION_LONG_CLICK) atual.isLongClickable else atual.isClickable
            if (aceita) return atual.performAction(acao)
            atual = atual.parent
            subidas++
        }
        return no.performAction(acao)
    }

    private fun desenharBarra(estado: EstadoBarra) {
        // Já na tela com a mesma estrutura: atualiza no lugar, sem piscar a barra real por baixo.
        val atual = estadoDesenhado
        if (overlay != null && atual != null && mesmaEstrutura(atual, estado)) {
            atualizarBarra(estado)
            return
        }
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val r = estado.bounds
        val params = WindowManager.LayoutParams(
            r.width(), r.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = r.left; y = r.top }

        removerOverlay()
        overlay = criarBarra(estado).also {
            runCatching { wm.addView(it, params) }
                .onFailure { e -> Log.w(TAG, "falha ao desenhar overlay", e); overlay = null }
        }
        estadoDesenhado = if (overlay != null) estado else null
        if (overlay != null) {
            ultimaConfirmacaoPacote = SystemClock.uptimeMillis()
            ultimaJanelaAtivaId = janelaAtivaId()
            handler.postDelayed(vigia, VIGIA_MS)
        }
    }

    /**
     * Enquanto o overlay está na tela, confere se a janela ativa ainda é o Instagram. Como o sistema
     * só entrega eventos do Instagram, o app não é avisado quando outro app/sistema assume a tela
     * (diálogo de permissão, compartilhar, outro app, tela de bloqueio): o overlay não pode ficar
     * por cima disso.
     */
    private val vigia = object : Runnable {
        override fun run() {
            if (overlay == null) return
            // A cada 80 ms: só metadados das janelas (id da ativa), sem tocar no processo do Instagram.
            // Lê o pacote da janela ativa quando ela MUDOU (um por mudança: diálogo do próprio
            // Instagram não repete a leitura) e, como rede de segurança, a cada VIGIA_PACOTE_MS.
            // Limite: janela de outro app que NÃO toma o foco (flutuante, PiP) não muda a janela
            // ativa; nesse caso a barra sai na confirmação periódica (até 0,6 s).
            val agora = SystemClock.uptimeMillis()
            val ativaId = janelaAtivaId()
            if (ativaId != ultimaJanelaAtivaId || agora - ultimaConfirmacaoPacote >= VIGIA_PACOTE_MS) {
                ultimaJanelaAtivaId = ativaId
                ultimaConfirmacaoPacote = agora
                val ativo = rootInActiveWindow?.packageName?.toString()
                if (ativo != PKG_INSTAGRAM) return esconderOverlay()
            }
            handler.postDelayed(this, VIGIA_MS)
        }
    }

    /** Id da janela ativa (-1 se não houver ou a lista vier vazia). Só metadados, sem IPC ao app. */
    @Suppress("DEPRECATION")
    private fun janelaAtivaId(): Int {
        val lista = windows ?: return -1
        val id = lista.firstOrNull { it.isActive }?.id ?: -1
        lista.forEach { it.recycle() }
        return id
    }

    /** Remove o overlay mantendo `ultimoEstado` (usado ao redesenhar). */
    private fun removerOverlay() {
        handler.removeCallbacks(vigia)
        overlay?.let { v ->
            runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
        }
        overlay = null
        estadoDesenhado = null
        ultimaJanelaAtivaId = -1
    }

    /** Remove o overlay e esquece o estado, para o próximo evento reavaliar do zero. */
    private fun esconderOverlay() {
        removerOverlay()
        ultimoEstado = null
        botoesReais.clear()
    }

    // ---------- Fase 1: modo de mapeamento (só build debug) ----------

    private fun despejarMapa(root: AccessibilityNodeInfo) {
        val agora = SystemClock.uptimeMillis()
        if (agora - ultimoMapa < MAPA_INTERVALO_MS) return
        ultimoMapa = agora
        Log.d("MAPA", "---- dump ---- uiMode=${if (modoEscuro()) "escuro" else "claro"}")
        despejarArvore(root)
    }

    private fun despejarArvore(node: AccessibilityNodeInfo?, nivel: Int = 0) {
        node ?: return
        val r = Rect().also { node.getBoundsInScreen(it) }
        Log.d(
            "MAPA", "  ".repeat(nivel) +
                "id=${node.viewIdResourceName} desc=${node.contentDescription} " +
                "texto=${node.text} sel=${node.isSelected} chk=${node.isChecked} foc=${node.isFocused} " +
                "a11yFoc=${node.isAccessibilityFocused} click=${node.isClickable} " +
                "state=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) node.stateDescription else null} " +
                "cls=${node.className} vis=${node.isVisibleToUser} $r"
        )
        for (i in 0 until node.childCount) despejarArvore(node.getChild(i), nivel + 1)
    }
}
