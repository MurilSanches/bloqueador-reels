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
