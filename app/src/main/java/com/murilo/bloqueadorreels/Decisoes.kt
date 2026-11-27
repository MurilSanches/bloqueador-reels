package com.murilo.bloqueadorreels

enum class AcaoReels { IGNORAR, VOLTAR, HOME }

/** Regras de decisão sem dependência do Android, para poderem ser testadas na JVM. */
object Decisoes {
    /** Depois de um "voltar", espera a transição antes de julgar que o Reels continua aberto. */
    const val IGNORAR_REPETICAO_MS = 1500L

    /** Se o Reels ainda está aberto após esse tempo, o "voltar" não resolveu: usa HOME. */
    const val USAR_HOME_ATE_MS = 4000L

    /** Tempo de uso do Instagram sem reconhecer nada antes de avisar o usuário. */
    const val LIMITE_SEM_RECONHECER_MS = 5 * 60_000L

    /** Pausa longa no uso: começa a contar de novo (evita somar usos de dias diferentes). */
    const val PAUSA_NOVA_SESSAO_MS = 30 * 60_000L

    fun acaoReels(msDesdeUltimoBloqueio: Long): AcaoReels = when {
        msDesdeUltimoBloqueio < IGNORAR_REPETICAO_MS -> AcaoReels.IGNORAR
        msDesdeUltimoBloqueio < USAR_HOME_ATE_MS -> AcaoReels.HOME
        else -> AcaoReels.VOLTAR
    }

    /** Depois de entrar no Instagram vindo de fora, por quanto tempo a entrada ainda conta como "por link". */
    const val ENTRADA_EXTERNA_MAX_MS = 20_000L

    /**
     * A entrada "de fora" (Instagram saiu de cena e voltou) só vale por um tempo: se o Reels não
     * apareceu logo, o usuário abriu o app normalmente e qualquer Reels depois disso é navegação.
     */
    fun entradaExternaValida(entradaExterna: Boolean, msDesdeEntrada: Long): Boolean =
        entradaExterna && msDesdeEntrada in 0..ENTRADA_EXTERNA_MAX_MS

    /**
     * Outro app/sistema assumiu a tela? Pacote nulo (raiz sumiu numa transição de tela do próprio
     * Instagram) NÃO é saída: tratá-lo assim gerava uma "entrada de fora" falsa e liberava Reels.
     */
    fun saiuDoInstagram(pacoteAtivo: String?, pacoteInstagram: String): Boolean =
        pacoteAtivo != null && pacoteAtivo != pacoteInstagram

    /** No primeiro Reels do viewer = o pager ainda não oferece "rolar para trás". */
    fun noPrimeiroDoViewer(podeRolarParaTras: Boolean): Boolean = !podeRolarParaTras

    /**
     * Reels aberto é permitido?
     * - por link: o Reels veio da entrada de fora (ou já estava liberado por ela) e o viewer ainda está
     *   no primeiro Reels; ao deslizar para o próximo a liberação acaba;
     * - por chat: o Reels traz o "enviado por" de uma conversa (conferido a cada leitura, então ao
     *   deslizar para um Reels que não é da conversa ele volta a ser bloqueado).
     * Com as duas opções desligadas nada é permitido.
     */
    fun reelsPermitido(
        permitirLink: Boolean,
        entradaExternaValida: Boolean,
        liberadoPorLink: Boolean,
        permitirChat: Boolean,
        viewerDoChat: Boolean,
        noPrimeiro: Boolean = true
    ): Boolean =
        (permitirLink && noPrimeiro && (entradaExternaValida || liberadoPorLink)) || (permitirChat && viewerDoChat)

    private val NUMERO_BADGE = Regex("^\\d{1,3}\\+?$")

    /**
     * Texto do aviso de mensagens não lidas: só número curto ("3", "99+"). Qualquer outra coisa
     * vira "" (apenas a bolinha), para o app nunca repetir texto arbitrário da tela do Instagram.
     */
    fun textoBadge(bruto: CharSequence?): String {
        val t = bruto?.toString()?.trim().orEmpty()
        return if (NUMERO_BADGE.matches(t)) t else ""
    }

    /** Aba ativa: a que o Instagram marcou, ou a última tocada na nossa barra. */
    fun abaAtiva(lidaDoInstagram: String?, ultimaTocada: String): String = lidaDoInstagram ?: ultimaTocada

    /**
     * Atualiza o relógio de "sem reconhecer". Devolve o novo valor de `semReconhecerDesde`
     * (0 = tudo bem). `reconheceu` = achou a barra ou a tela de Reels nesta leitura.
     */
    fun semReconhecerDesde(
        atual: Long,
        reconheceu: Boolean,
        agora: Long,
        ultimoUso: Long
    ): Long = when {
        reconheceu -> 0L
        atual == 0L || agora - ultimoUso > PAUSA_NOVA_SESSAO_MS -> agora
        else -> atual
    }

    fun avisarBarraNaoReconhecida(semReconhecerDesde: Long, ultimoUso: Long): Boolean =
        semReconhecerDesde > 0L && ultimoUso - semReconhecerDesde > LIMITE_SEM_RECONHECER_MS
}
