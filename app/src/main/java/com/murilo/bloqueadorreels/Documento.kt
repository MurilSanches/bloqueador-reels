package com.murilo.bloqueadorreels

enum class TipoBloco { TITULO, SECAO, PARAGRAFO, ITEM }

data class Bloco(val tipo: TipoBloco, val texto: String)

/**
 * Lê os textos legais (arquivos .md da pasta assets, um bloco por linha) sem depender do Android, para poderem ser
 * testados na JVM. Formato aceito: `# título`, `## seção`, `- item`, linha comum = parágrafo,
 * `**negrito**`, `` `código` `` (sem a crase) e tokens `{{NOME}}`.
 */
object Documento {

    fun blocos(markdown: String, tokens: Map<String, String> = emptyMap()): List<Bloco> =
        markdown.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { linha -> trocarTokens(linha, tokens).replace("`", "") }
            .map { linha ->
                when {
                    linha.startsWith("## ") -> Bloco(TipoBloco.SECAO, linha.removePrefix("## ").trim())
                    linha.startsWith("# ") -> Bloco(TipoBloco.TITULO, linha.removePrefix("# ").trim())
                    linha.startsWith("- ") -> Bloco(TipoBloco.ITEM, linha.removePrefix("- ").trim())
                    else -> Bloco(TipoBloco.PARAGRAFO, linha)
                }
            }
            .toList()

    private fun trocarTokens(texto: String, tokens: Map<String, String>): String =
        tokens.entries.fold(texto) { acc, (nome, valor) -> acc.replace("{{$nome}}", valor) }

    /** Divide em trechos (texto, negrito) a partir dos pares de `**`. */
    fun trechos(texto: String): List<Pair<String, Boolean>> {
        val saida = mutableListOf<Pair<String, Boolean>>()
        texto.split("**").forEachIndexed { i, parte ->
            if (parte.isNotEmpty()) saida += parte to (i % 2 == 1)
        }
        return saida
    }
}
