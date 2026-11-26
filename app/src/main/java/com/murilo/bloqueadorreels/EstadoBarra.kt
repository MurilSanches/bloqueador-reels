package com.murilo.bloqueadorreels

import android.graphics.Rect

data class EstadoBarra(
    val visivel: Boolean,
    val bounds: Rect,
    val botoes: List<String>,      // ordem atual, ex: [home, search, create, reels, profile]
    val selecionado: String?,      // aba ativa
    val modoEscuro: Boolean,
    /**
     * Aviso de não lidas por botão. Valor = número ("3", "99+") ou "" (só a bolinha, quando o
     * Instagram não mostra número). Chave ausente = sem aviso.
     */
    val badges: Map<String, String> = emptyMap()
)
