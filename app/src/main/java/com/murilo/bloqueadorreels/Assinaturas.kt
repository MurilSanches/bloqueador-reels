package com.murilo.bloqueadorreels

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AssinaturaBotao(
    val chave: String,              // "home", "search", "create", "reels", "profile"...
    val ids: List<String>,          // IDs possíveis (view ids completos)
    val descricoes: List<String>    // "Início", "Home", "Página inicial"...
)

data class Assinaturas(
    val versao: Int,
    val containerBarraIds: List<String>,
    val reelsViewerIds: List<String>,
    val botoes: List<AssinaturaBotao>,
    /** IDs do contador de não lidas dentro de um botão (ex.: bolinha das mensagens). */
    val badgeIds: List<String> = emptyList(),
    /**
     * IDs que só existem no Reels aberto a partir de uma conversa ("enviado por"). Só a presença
     * da view é conferida; nome e texto nunca são lidos.
     */
    val reelsChatIds: List<String> = emptyList()
) {
    /**
     * Reconhece o botão por ID (exato) ou, se o ID não bater, por descrição de conteúdo.
     * Devolve a chave ("home", "reels"...) ou null se nada bater.
     */
    fun identificar(id: String?, descricao: CharSequence?): String? {
        if (!id.isNullOrEmpty()) {
            botoes.firstOrNull { id in it.ids }?.let { return it.chave }
        }
        val d = descricao?.toString()?.trim()?.lowercase().orEmpty()
        if (d.isEmpty()) return null
        return botoes.firstOrNull { b -> b.descricoes.any { combina(d, it.lowercase()) } }?.chave
    }

    /**
     * Sanidade do conteúdo (arquivo local hoje; JSON remoto no passo 7). Limita tamanho e exige que
     * todo ID seja do Instagram, para um arquivo adulterado não fazer o serviço agir em outro app
     * nem gastar CPU com listas enormes. Sem "reels" não há o que esconder.
     */
    fun valida(): Boolean {
        fun curto(s: String, max: Int) = s.isNotBlank() && s.length <= max
        if (containerBarraIds.size !in 1..MAX_IDS || reelsViewerIds.size !in 1..MAX_IDS) return false
        if (badgeIds.size > MAX_IDS || reelsChatIds.size > MAX_IDS) return false
        if (botoes.size !in 1..MAX_BOTOES || botoes.none { it.chave == "reels" }) return false
        val ids = containerBarraIds + reelsViewerIds + badgeIds + reelsChatIds + botoes.flatMap { it.ids }
        if (ids.any { !curto(it, MAX_TEXTO) || !it.startsWith(PREFIXO_ID_INSTAGRAM) }) return false
        return botoes.all { b ->
            curto(b.chave, 32) && b.ids.size <= MAX_IDS && b.descricoes.size <= MAX_DESCRICOES &&
                b.descricoes.all { curto(it, MAX_TEXTO) }
        }
    }

    // "home" bate com "home", "home, 2 novas" e "home 2"; não bate com "homestead".
    private fun combina(descricao: String, assinatura: String) =
        descricao == assinatura ||
            descricao.startsWith("$assinatura,") ||
            descricao.startsWith("$assinatura ")

    companion object {
        const val ASSET = "assinaturas.json"
        const val ARQUIVO_LOCAL = "assinaturas.json" // sobrescreve o asset (passo 7: vem do servidor)
        const val PREFIXO_ID_INSTAGRAM = "com.instagram.android:id/"
        const val MAX_BYTES_ARQUIVO = 64 * 1024L
        private const val MAX_BOTOES = 20
        private const val MAX_IDS = 8
        private const val MAX_DESCRICOES = 16
        private const val MAX_TEXTO = 200

        /** Lê e valida; devolve null se o texto for inválido, malformado ou fora dos limites. */
        fun deJsonOuNull(texto: String): Assinaturas? =
            if (texto.length > MAX_BYTES_ARQUIVO) null
            else runCatching { deJson(texto) }.getOrNull()?.takeIf { it.valida() }

        fun deJson(texto: String): Assinaturas {
            val o = JSONObject(texto)
            val arr = o.getJSONArray("botoes")
            val botoes = (0 until arr.length()).map { i ->
                val b = arr.getJSONObject(i)
                AssinaturaBotao(
                    chave = b.getString("chave"),
                    ids = b.optJSONArray("ids").paraLista(),
                    descricoes = b.optJSONArray("descricoes").paraLista()
                )
            }
            return Assinaturas(
                versao = o.optInt("versao", 1),
                containerBarraIds = o.optJSONArray("containerBarraIds").paraLista(),
                reelsViewerIds = o.optJSONArray("reelsViewerIds").paraLista(),
                botoes = botoes,
                badgeIds = o.optJSONArray("badgeIds").paraLista(),
                reelsChatIds = o.optJSONArray("reelsChatIds").paraLista()
            )
        }

        /** Arquivo local (se existir e for válido) ganha do asset embutido. */
        fun carregar(ctx: Context): Assinaturas {
            val local = File(ctx.filesDir, ARQUIVO_LOCAL)
            // Arquivo local só vale se for pequeno e passar na validação; senão usa o asset embutido.
            if (local.isFile && local.length() <= MAX_BYTES_ARQUIVO) {
                runCatching { deJsonOuNull(local.readText()) }.getOrNull()?.let { return it }
            }
            return deJson(ctx.assets.open(ASSET).bufferedReader().use { it.readText() })
        }

        private fun JSONArray?.paraLista(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { getString(it) }
    }
}
