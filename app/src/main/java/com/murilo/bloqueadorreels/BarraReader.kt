package com.murilo.bloqueadorreels

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

class LeituraBarra(
    val estado: EstadoBarra,
    val botoesReais: Map<String, AccessibilityNodeInfo>
)

/** `lerEstado` da fase 2: tira o "retrato" da barra de navegação do Instagram. */
object BarraReader {
    private const val PROFUNDIDADE_MAX = 8
    private const val MIN_BOTOES = 3

    /**
     * Devolve null quando não tem certeza do que está na tela (container não achado,
     * poucos botões reconhecidos ou aba Reels ausente). Regra: na dúvida, não desenha.
     */
    fun ler(root: AccessibilityNodeInfo, assin: Assinaturas, modoEscuro: Boolean): LeituraBarra? {
        val container = acharContainer(root, assin) ?: return null
        val bounds = Rect().also { container.getBoundsInScreen(it) }
        val visivel = container.isVisibleToUser && bounds.height() > 0 && bounds.width() > 0

        val achados = linkedMapOf<String, AccessibilityNodeInfo>()
        coletar(container, assin, achados, 0)
        if (achados.size < MIN_BOTOES || "reels" !in achados) return null

        val ordenados = achados.entries.sortedBy { (_, n) -> Rect().also { n.getBoundsInScreen(it) }.left }
        val selecionado = ordenados.firstOrNull { (_, n) -> selecionado(n) }?.key

        val badges = ordenados.mapNotNull { (chave, no) -> badge(no, assin)?.let { chave to it } }.toMap()

        return LeituraBarra(
            EstadoBarra(visivel, bounds, ordenados.map { it.key }, selecionado, modoEscuro, badges),
            ordenados.associate { it.key to it.value }
        )
    }

    /**
     * Aviso de não lidas dentro do botão: o número, se for um número curto, ou "" (só a bolinha).
     * null = não há aviso. Só o nó do contador é lido, e só o que for número vira texto.
     */
    private fun badge(no: AccessibilityNodeInfo, assin: Assinaturas, nivel: Int = 0): String? {
        if (assin.badgeIds.isEmpty() || nivel > 3) return null
        for (i in 0 until no.childCount) {
            val filho = no.getChild(i) ?: continue
            if (filho.viewIdResourceName in assin.badgeIds && filho.isVisibleToUser) {
                return Decisoes.textoBadge(filho.text)
            }
            badge(filho, assin, nivel + 1)?.let { return it }
        }
        return null
    }

    private fun acharContainer(root: AccessibilityNodeInfo, assin: Assinaturas): AccessibilityNodeInfo? {
        for (id in assin.containerBarraIds) {
            val nos = root.findAccessibilityNodeInfosByViewId(id)
            nos.firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        return null
    }

    // Busca só dentro do container (desempenho), nunca na árvore inteira.
    private fun coletar(
        no: AccessibilityNodeInfo,
        assin: Assinaturas,
        saida: MutableMap<String, AccessibilityNodeInfo>,
        nivel: Int
    ) {
        if (nivel > PROFUNDIDADE_MAX) return
        val chave = assin.identificar(no.viewIdResourceName, no.contentDescription)
        if (chave != null && chave !in saida) {
            saida[chave] = no
            return // não precisa descer dentro de um botão já reconhecido
        }
        for (i in 0 until no.childCount) {
            val filho = no.getChild(i) ?: continue
            coletar(filho, assin, saida, nivel + 1)
        }
    }

    private fun selecionado(no: AccessibilityNodeInfo, nivel: Int = 0): Boolean {
        if (no.isSelected) return true
        if (nivel >= 2) return false
        for (i in 0 until no.childCount) {
            val filho = no.getChild(i) ?: continue
            if (selecionado(filho, nivel + 1)) return true
        }
        return false
    }
}
