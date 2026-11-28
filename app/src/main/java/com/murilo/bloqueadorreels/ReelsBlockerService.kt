package com.murilo.bloqueadorreels

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class ReelsBlockerService : AccessibilityService() {

    companion object {
        private const val TAG = "ReelsBlocker"
        private const val DEBOUNCE_MS = 30L
        private const val INTERVALO_MIN_MS = 250L
        private const val VIGIA_MS = 80L          // consulta leve (só metadados de janelas)
        private const val VIGIA_PACOTE_MS = 600L  // confirmação periódica pelo pacote da janela ativa
        private const val ABA_PENDENTE_MS = 500L
        private const val AVISO_INTERVALO_MS = 3000L
        private const val MAPA_INTERVALO_MS = 1500L
        private const val GRAVAR_SAUDE_A_CADA_MS = 30_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val atualizar = Runnable { verificarBarra() }

    private lateinit var alvo: InstagramAlvo
    private var barra: BarraOverlay? = null // criada ao conectar; nula antes (e se o serviço nunca conectou)
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

    // Reels permitido (opções "link" e "chat"). Só vale com as opções ligadas no app.
    private var permitirLink = false
    private var permitirChat = false
    private var foraDoInstagram = true   // o Instagram não estava na tela; a próxima aparição é uma "entrada"
    private var entradaExterna = false   // a entrada atual pode ter vindo de um link de outro app
    private var entradaDesde = 0L
    private var liberadoPorLink = false  // Reels liberado pela entrada; segue liberado até sair dele
    private var vigiaAgendada = false
    private var ultimoReelsPermitido = false


    // Saúde: avisa o usuário se o Instagram é usado por muito tempo sem reconhecer nada.
    private var semReconhecerDesde = 0L
    private var ultimoUsoInstagram = 0L
    private var ultimaGravacaoSaude = 0L

    private val ouvinte = SharedPreferences.OnSharedPreferenceChangeListener { _, chave ->
        if (chave == Prefs.PERMITIR_REELS_LINK || chave == Prefs.PERMITIR_REELS_CHAT) carregarOpcoes()
        if (chave == Prefs.IG_OCULTAR_REELS || chave == Prefs.CONSENTIMENTO ||
            chave == Prefs.PERMITIR_REELS_LINK || chave == Prefs.PERMITIR_REELS_CHAT
        ) {
            ultimoEstado = null // força reavaliar
            if (!ativoParaInstagram()) esconderOverlay() else verificarBarra()
        }
    }

    /** O serviço só age com o aviso aceito no app e o interruptor ligado. */
    private fun ativoParaInstagram() = Prefs.consentimento(this) && Prefs.ocultarReels(this)

    private fun carregarOpcoes() {
        permitirLink = Prefs.permitirReelsLink(this)
        permitirChat = Prefs.permitirReelsChat(this)
        if (!permitirLink) { entradaExterna = false; liberadoPorLink = false }
    }

    override fun onServiceConnected() {
        alvo = InstagramAlvo(Assinaturas.carregar(this), this)
        barra?.remover() // religação do serviço: não deixa janela antiga órfã
        barra = BarraOverlay(this, alvo.estilo) { chave, longo ->
            acionarBotaoReal(
                chave,
                if (longo) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
            )
        }
        carregarOpcoes()
        foraDoInstagram = true
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
        vigiaAgendada = false
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

        if (pacote != alvo.pacote) {
            // O sistema já filtra por pacote (accessibility_config.xml); isto é só uma segunda
            // barreira: nada de outro app é lido, e o overlay sai junto.
            if (janelaMudou) esconderOverlay()
            return
        }
        if (!ativoParaInstagram()) {
            esconderOverlay()
            return
        }
        // Instagram voltou à tela depois de ter saído (link do WhatsApp, launcher, recentes...): é uma
        // "entrada". Só conta como possível link se a opção estiver ligada.
        if (foraDoInstagram) {
            foraDoInstagram = false
            entradaExterna = permitirLink
            entradaDesde = SystemClock.uptimeMillis()
        }
        if (permitirLink) garantirVigia() // percebe quando o usuário sai do Instagram, mesmo sem overlay
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
        if (root == null || root.packageName?.toString() != alvo.pacote) return esconderOverlay()

        if (Prefs.ehDebug(this) && Prefs.mapeamento(this)) despejarMapa(root)

        // Fase 4: tela de Reels aberta -> volta, salvo se veio de link/chat e a opção está ligada.
        val idReels = alvo.telaVideoCurto(root)
        if (idReels != null && !reelsPermitido(root, idReels)) {
            bloquearReels(idReels)
            registrarSaude(reconheceu = true)
            return esconderOverlay()
        }

        // Fase 2: lê a barra. Sem certeza -> não desenha nada.
        val leitura = alvo.lerBarra(root, modoEscuro())
        registrarSaude(reconheceu = leitura != null || idReels != null)
        if (idReels == null) {
            ultimoReelsPermitido = false
            // Tela normal do Instagram (com a barra): a entrada de fora já foi consumida.
            if (leitura != null) { entradaExterna = false; liberadoPorLink = false }
        }
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
        if (!estado.visivel) removerOverlay() else mostrarBarra(estado)
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

    private fun algumVisivel(root: AccessibilityNodeInfo, id: String) =
        root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser }

    /**
     * Reels aberto: vale ver? Link = o Instagram acabou de entrar vindo de fora, e só vale para o
     * PRIMEIRO Reels do viewer (ao deslizar para o próximo a liberação do link se encerra). Chat = o
     * Reels traz o "enviado por" de uma conversa (só confere se a view existe, nunca lê nome ou texto).
     */
    private fun reelsPermitido(root: AccessibilityNodeInfo, idReels: String): Boolean {
        if (!permitirLink && !permitirChat) return false
        val primeiro = noPrimeiroReels(root, idReels)
        if (!primeiro) { entradaExterna = false; liberadoPorLink = false } // deslizou: liberação do link acabou
        val entrada = Decisoes.entradaExternaValida(entradaExterna, SystemClock.uptimeMillis() - entradaDesde)
        val viaChat = permitirChat && alvo.assinaturas.reelsChatIds.any { algumVisivel(root, it) }
        val ok = Decisoes.reelsPermitido(permitirLink, entrada, liberadoPorLink, permitirChat, viaChat, primeiro)
        if (permitirLink && primeiro && (entrada || liberadoPorLink)) liberadoPorLink = true
        if (ok != ultimoReelsPermitido) {
            ultimoReelsPermitido = ok
            Log.i(TAG, "Reels permitido=$ok (entradaDeFora=$entrada, liberadoPorLink=$liberadoPorLink, chat=$viaChat, primeiro=$primeiro)")
        }
        return ok
    }

    /**
     * O viewer está no primeiro Reels? Medido no emulador: no primeiro o pager só oferece "rolar para
     * frente"; depois de deslizar passa a oferecer também "rolar para trás". Não lê conteúdo.
     */
    private fun noPrimeiroReels(root: AccessibilityNodeInfo, idReels: String): Boolean {
        val pager = root.findAccessibilityNodeInfosByViewId(idReels).firstOrNull { it.isVisibleToUser } ?: return false
        return Decisoes.noPrimeiroDoViewer(pager.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD })
    }

    private fun bloquearReels(idReels: String) {
        val agora = SystemClock.uptimeMillis()
        val acao = Decisoes.acaoReels(agora - ultimoBloqueio)
        if (acao == AcaoReels.IGNORAR) return // "voltar" ainda em andamento
        ultimoBloqueio = agora
        Log.i(TAG, "tela de Reels ($idReels): $acao")
        performGlobalAction(if (acao == AcaoReels.HOME) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)

        if (agora - ultimoAviso > AVISO_INTERVALO_MS) {
            ultimoAviso = agora
            Toast.makeText(this, R.string.reels_bloqueado, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Fase 3 ----------

    /** A barra substituta sempre acompanha o tema do celular (o Instagram segue o sistema por padrão). */
    private fun modoEscuro() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun acionarBotaoReal(chave: String, acao: Int) {
        val no = botoesReais[chave]
        val ok = no != null && executar(no, acao)
        // "Criar" abre o compositor, não vira aba: não ganha destaque.
        if (ok && acao == AccessibilityNodeInfo.ACTION_CLICK && chave != "create") {
            abaLocal = chave
            abaPendente = chave
            abaPendenteAte = SystemClock.uptimeMillis() + ABA_PENDENTE_MS
            // Destaque imediato, sem recriar a janela: a releitura abaixo só confirma.
            barra?.destacar(chave)?.let { ultimoEstado = it }
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

    private fun mostrarBarra(estado: EstadoBarra) {
        // Só uma janela nova precisa (re)armar a vigia; atualização no lugar não.
        val resultado = barra?.mostrar(estado)
        // addView falhou: esquece o estado para a próxima leitura tentar desenhar de novo.
        if (resultado == BarraOverlay.Resultado.FALHOU) ultimoEstado = null
        if (resultado != BarraOverlay.Resultado.CRIADO) return
        ultimaConfirmacaoPacote = SystemClock.uptimeMillis()
        ultimaJanelaAtivaId = janelaAtivaId()
        garantirVigia()
    }

    /**
     * Enquanto o overlay está na tela, confere se a janela ativa ainda é o Instagram. Como o sistema
     * só entrega eventos do Instagram, o app não é avisado quando outro app/sistema assume a tela
     * (diálogo de permissão, compartilhar, outro app, tela de bloqueio): o overlay não pode ficar
     * por cima disso.
     */
    private val vigia = object : Runnable {
        override fun run() {
            vigiaAgendada = false
            // Sem overlay só continua se a opção "link" precisa saber quando o Instagram sai de cena.
            if (barra?.visivel != true && (foraDoInstagram || !permitirLink)) return
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
                if (ativo == null) {
                    // Transição de tela (ex.: abrir conversa): a raiz some por instantes. Não é sair do
                    // Instagram; só tira a barra e relê logo (senão virava "entrada de fora" e liberava Reels).
                    esconderOverlay()
                    agendar(DEBOUNCE_MS)
                } else if (Decisoes.saiuDoInstagram(ativo, alvo.pacote)) return sairDoInstagram()
            }
            vigiaAgendada = true
            handler.postDelayed(this, VIGIA_MS)
        }
    }

    /** Um único agendamento da vigia por vez (eventos do Instagram chegam o tempo todo). */
    private fun garantirVigia() {
        if (vigiaAgendada) return
        vigiaAgendada = true
        handler.postDelayed(vigia, VIGIA_MS)
    }

    /** Outro app/sistema assumiu a tela: some o overlay e a próxima volta ao Instagram é uma nova entrada. */
    private fun sairDoInstagram() {
        foraDoInstagram = true
        entradaExterna = false
        liberadoPorLink = false
        ultimoReelsPermitido = false
        esconderOverlay()
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
        barra?.remover()
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
