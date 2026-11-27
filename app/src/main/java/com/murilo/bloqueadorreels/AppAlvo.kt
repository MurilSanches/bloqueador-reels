package com.murilo.bloqueadorreels

import android.view.accessibility.AccessibilityNodeInfo

/** Aparência da barra substituta de uma plataforma: ícones, descrições e cores. */
interface EstiloBarra {
    /** Recurso `@DrawableRes` do ícone do botão, no estado ativo (preenchido) ou inativo (contorno). */
    fun icone(chave: String, ativo: Boolean): Int
    fun descricao(chave: String): String
    /** Botões que a barra substituta mostra (o Instagram, por exemplo, esconde "reels"). */
    fun botoesVisiveis(botoes: List<String>): List<String>
    fun corFundo(escuro: Boolean): Int
    fun corPilula(escuro: Boolean): Int
    fun corIcone(escuro: Boolean): Int
}

/** Uma plataforma vigiada (Instagram hoje; YouTube/TikTok depois). */
interface AppAlvo {
    val pacote: String
    val estilo: EstiloBarra

    /** Retrato da barra de navegação, ou null se não houver certeza (na dúvida, não desenha). */
    fun lerBarra(root: AccessibilityNodeInfo, modoEscuro: Boolean): LeituraBarra?

    /** ID que casou se a tela é de vídeo curto (Reels, Shorts...); null se não é. */
    fun telaVideoCurto(root: AccessibilityNodeInfo): String?
}
