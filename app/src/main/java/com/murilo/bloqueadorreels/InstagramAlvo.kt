package com.murilo.bloqueadorreels

import android.content.Context
import android.graphics.Color
import android.view.accessibility.AccessibilityNodeInfo

class InstagramAlvo(val assinaturas: Assinaturas, ctx: Context) : AppAlvo {

    companion object {
        const val PACOTE = "com.instagram.android"
    }

    override val pacote = PACOTE
    override val estilo: EstiloBarra = InstagramEstilo(ctx)

    override fun lerBarra(root: AccessibilityNodeInfo, modoEscuro: Boolean): LeituraBarra? =
        BarraReader.ler(root, assinaturas, modoEscuro)

    /**
     * ID principal do visualizador de Reels; se ele não está na tela, tenta os IDs de reserva, que só
     * valem juntos (`Decisoes.telaVideoCurto`). A consulta de reserva para assim que há o bastante.
     */
    override fun telaVideoCurto(root: AccessibilityNodeInfo): String? {
        assinaturas.reelsViewerIds.firstOrNull { visivel(root, it) }?.let { return it }
        val achados = mutableListOf<String>()
        for (id in assinaturas.reelsReservaIds) {
            if (visivel(root, id)) achados += id
            if (Decisoes.telaVideoCurto(false, achados.size)) return achados.first()
        }
        return null
    }

    private fun visivel(root: AccessibilityNodeInfo, id: String) =
        root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser }
}

/** Aparência da barra do Instagram: contorno quando inativo, preenchido quando ativo. */
class InstagramEstilo(private val ctx: Context) : EstiloBarra {

    override fun icone(chave: String, ativo: Boolean) = when (chave) {
        "home" -> if (ativo) R.drawable.ic_nav_home_on else R.drawable.ic_nav_home
        "search" -> if (ativo) R.drawable.ic_nav_search_on else R.drawable.ic_nav_search
        "create" -> if (ativo) R.drawable.ic_nav_create_on else R.drawable.ic_nav_create
        "shop" -> if (ativo) R.drawable.ic_nav_shop_on else R.drawable.ic_nav_shop
        "messages" -> if (ativo) R.drawable.ic_nav_messages_on else R.drawable.ic_nav_messages
        else -> if (ativo) R.drawable.ic_nav_profile_on else R.drawable.ic_nav_profile
    }

    override fun descricao(chave: String): String = ctx.getString(
        when (chave) {
            "home" -> R.string.desc_home
            "search" -> R.string.desc_search
            "create" -> R.string.desc_create
            "shop" -> R.string.desc_shop
            "messages" -> R.string.desc_messages
            else -> R.string.desc_profile
        }
    )

    override fun botoesVisiveis(botoes: List<String>) = botoes.filter { it != "reels" }

    // Cores medidas no Instagram: fundo claro #FFFFFF, escuro #0C1014 (não é preto puro);
    // pílula atrás do ícone ativo: claro #F0F2F5, escuro #25282D; ícones sempre preto/branco.
    override fun corFundo(escuro: Boolean) = if (escuro) Color.parseColor("#0C1014") else Color.WHITE
    override fun corPilula(escuro: Boolean) = Color.parseColor(if (escuro) "#25282D" else "#F0F2F5")
    override fun corIcone(escuro: Boolean) = if (escuro) Color.WHITE else Color.BLACK
}
