package com.murilo.bloqueadorreels

import android.content.Context

object Prefs {
    const val ARQUIVO = "config"
    const val IG_OCULTAR_REELS = "ig_ocultar_reels"
    const val CONSENTIMENTO = "consentimento_acessibilidade"
    const val MODO_MAPEAMENTO = "modo_mapeamento"
    const val TEMA_APP = "tema_app"
    const val PERMITIR_REELS_LINK = "permitir_reels_link"
    const val PERMITIR_REELS_CHAT = "permitir_reels_chat"

    const val SAUDE_SEM_RECONHECER_DESDE = "saude_sem_reconhecer_desde"
    const val SAUDE_ULTIMO_USO = "saude_ultimo_uso"

    fun semReconhecerDesde(ctx: Context) = prefs(ctx).getLong(SAUDE_SEM_RECONHECER_DESDE, 0L)
    fun ultimoUsoInstagram(ctx: Context) = prefs(ctx).getLong(SAUDE_ULTIMO_USO, 0L)
    fun gravarSaude(ctx: Context, semReconhecerDesde: Long, ultimoUso: Long) =
        prefs(ctx).edit()
            .putLong(SAUDE_SEM_RECONHECER_DESDE, semReconhecerDesde)
            .putLong(SAUDE_ULTIMO_USO, ultimoUso)
            .apply()

    /** Recursos de desenvolvimento (mapeamento) só existem em build debug. */
    fun ehDebug(ctx: Context) =
        (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    const val TEMA_AUTO = "auto"
    const val TEMA_CLARO = "claro"
    const val TEMA_ESCURO = "escuro"

    /** Aparência do próprio app: segue o celular (padrão) até o usuário usar o interruptor. */
    fun temaApp(ctx: Context): String = prefs(ctx).getString(TEMA_APP, TEMA_AUTO) ?: TEMA_AUTO
    fun setTemaApp(ctx: Context, valor: String) =
        prefs(ctx).edit().putString(TEMA_APP, valor).apply()

    fun prefs(ctx: Context) = ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    fun ocultarReels(ctx: Context) = prefs(ctx).getBoolean(IG_OCULTAR_REELS, true)
    fun setOcultarReels(ctx: Context, valor: Boolean) =
        prefs(ctx).edit().putBoolean(IG_OCULTAR_REELS, valor).apply()

    /** Deixa ver o Reels aberto por um link de fora do Instagram (ex.: WhatsApp). Padrão: desligado. */
    fun permitirReelsLink(ctx: Context) = prefs(ctx).getBoolean(PERMITIR_REELS_LINK, false)
    fun setPermitirReelsLink(ctx: Context, valor: Boolean) =
        prefs(ctx).edit().putBoolean(PERMITIR_REELS_LINK, valor).apply()

    /** Deixa ver o Reels enviado numa conversa (mensagem direta) do Instagram. Padrão: desligado. */
    fun permitirReelsChat(ctx: Context) = prefs(ctx).getBoolean(PERMITIR_REELS_CHAT, false)
    fun setPermitirReelsChat(ctx: Context, valor: Boolean) =
        prefs(ctx).edit().putBoolean(PERMITIR_REELS_CHAT, valor).apply()

    fun consentimento(ctx: Context) = prefs(ctx).getBoolean(CONSENTIMENTO, false)
    fun setConsentimento(ctx: Context, valor: Boolean) =
        prefs(ctx).edit().putBoolean(CONSENTIMENTO, valor).apply()

    /** Só para desenvolvimento: despeja a árvore de views do Instagram no Logcat (tag MAPA). */
    fun mapeamento(ctx: Context) = prefs(ctx).getBoolean(MODO_MAPEAMENTO, false)
    fun setMapeamento(ctx: Context, valor: Boolean) =
        prefs(ctx).edit().putBoolean(MODO_MAPEAMENTO, valor).apply()
}
