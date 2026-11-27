package com.murilo.bloqueadorreels

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Barra substituta desenhada por cima da barra do app vigiado. Não sabe nada de Instagram: a
 * aparência vem do [EstiloBarra] e os toques saem por [aoTocar]. [servico] precisa ser o próprio
 * AccessibilityService (só ele pode abrir janela `TYPE_ACCESSIBILITY_OVERLAY`).
 */
class BarraOverlay(
    private val servico: AccessibilityService,
    private val estilo: EstiloBarra,
    private val aoTocar: (chave: String, longo: Boolean) -> Unit
) {
    enum class Resultado { ATUALIZADO, CRIADO, FALHOU }

    private companion object {
        const val TAG = "ReelsBlocker"
    }

    private var view: ViewGroup? = null

    var estadoDesenhado: EstadoBarra? = null
        private set

    val visivel: Boolean get() = view != null

    /**
     * Mostra a barra. Se já está na tela com a mesma estrutura (bounds, tema, botões), só atualiza
     * no lugar, sem remover a janela (senão a barra real piscava por baixo).
     */
    fun mostrar(estado: EstadoBarra): Resultado {
        val atual = estadoDesenhado
        if (view != null && atual != null && mesmaEstrutura(atual, estado)) {
            atualizar(estado)
            return Resultado.ATUALIZADO
        }
        val r = estado.bounds
        val params = WindowManager.LayoutParams(
            r.width(), r.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = r.left; y = r.top }

        remover()
        val nova = criarBarra(estado)
        val ok = runCatching { janela().addView(nova, params) }
            .onFailure { e -> Log.w(TAG, "falha ao desenhar overlay", e) }
            .isSuccess
        if (!ok) return Resultado.FALHOU
        view = nova
        estadoDesenhado = estado
        return Resultado.CRIADO
    }

    fun remover() {
        view?.let { v -> runCatching { janela().removeView(v) } }
        view = null
        estadoDesenhado = null
    }

    /** Destaque imediato de uma aba (toque na nossa barra), sem recriar a janela. Devolve o novo estado. */
    fun destacar(chave: String): EstadoBarra? {
        val atual = estadoDesenhado ?: return null
        if (view == null) return null
        val novo = atual.copy(selecionado = chave)
        atualizar(novo)
        return novo
    }

    private fun janela() = servico.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun dp(valor: Float) = (valor * servico.resources.displayMetrics.density).toInt()

    private fun ehVertical(estado: EstadoBarra) = estado.bounds.height() > estado.bounds.width()

    private fun mesmaEstrutura(a: EstadoBarra, b: EstadoBarra) =
        a.bounds == b.bounds && a.modoEscuro == b.modoEscuro &&
            estilo.botoesVisiveis(a.botoes) == estilo.botoesVisiveis(b.botoes)

    /** Atualiza no lugar só os botões cuja aba ativa ou aviso mudou. */
    private fun atualizar(novo: EstadoBarra) {
        val barra = view ?: return
        val antigo = estadoDesenhado ?: return
        estilo.botoesVisiveis(novo.botoes).forEachIndexed { i, chave ->
            val mudou = (chave == antigo.selecionado) != (chave == novo.selecionado) ||
                antigo.badges[chave] != novo.badges[chave]
            if (mudou) (barra.getChildAt(i) as? FrameLayout)?.let { preencherEspaco(it, chave, novo) }
        }
        estadoDesenhado = novo
    }

    private fun criarBarra(estado: EstadoBarra): ViewGroup {
        // Container alto e estreito (trilho lateral em tablet/dobrável): empilha na vertical.
        val vertical = ehVertical(estado)
        val barra = LinearLayout(servico).apply {
            orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(estilo.corFundo(estado.modoEscuro))
        }
        estilo.botoesVisiveis(estado.botoes).forEach { chave ->
            val espaco = FrameLayout(servico).apply {
                layoutParams = if (vertical) LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
                else LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
            }
            preencherEspaco(espaco, chave, estado)
            barra.addView(espaco)
        }
        return barra
    }

    /** Conteúdo de um botão (pílula, ícone, aviso). Refeito só nos botões que mudaram. */
    private fun preencherEspaco(espaco: FrameLayout, chave: String, estado: EstadoBarra) {
        val ativo = chave == estado.selecionado
        val vertical = ehVertical(estado)
        espaco.removeAllViews()
        val botao = ImageButton(servico).apply {
            setImageResource(estilo.icone(chave, ativo)) // desenhos próprios no mesmo estilo
            scaleType = ImageView.ScaleType.CENTER        // 24dp, como no Instagram
            // Sem foco de teclado/D-pad: o destaque de foco padrão pintava um retângulo cinza.
            isFocusable = false
            isFocusableInTouchMode = false
            defaultFocusHighlightEnabled = false
            imageTintList = ColorStateList.valueOf(estilo.corIcone(estado.modoEscuro))
            isSelected = ativo
            background = null
            contentDescription = estilo.descricao(chave)
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setOnClickListener { aoTocar(chave, false) }
            // Toque longo (ex.: trocar de conta no Perfil) também chega ao botão real.
            setOnLongClickListener {
                aoTocar(chave, true)
                true
            }
        }
        if (ativo && !vertical) espaco.addView(criarPilula(estado.modoEscuro))
        espaco.addView(botao)
        estado.badges[chave]?.let { espaco.addView(criarBadge(it)) }
    }

    /** Pílula atrás do ícone ativo. */
    private fun criarPilula(escuro: Boolean): View = View(servico).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20f).toFloat()
            setColor(estilo.corPilula(escuro))
        }
        layoutParams = FrameLayout.LayoutParams(dp(72f), dp(40f), Gravity.CENTER)
    }

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
            return View(servico).apply {
                background = fundo.apply { cornerRadius = dp(4f).toFloat() }
                layoutParams = FrameLayout.LayoutParams(dp(6.5f), dp(6.5f), Gravity.CENTER)
                translationX = dp(11.5f).toFloat()
                translationY = dp(7f).toFloat()
            }
        }
        return TextView(servico).apply {
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
}
