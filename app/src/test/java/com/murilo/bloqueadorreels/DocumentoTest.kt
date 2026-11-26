package com.murilo.bloqueadorreels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DocumentoTest {

    private val politica = File("src/main/assets/politica-de-privacidade.md").readText()
    private val termos = File("src/main/assets/termos-de-uso.md").readText()
    private val tokens = mapOf("RESPONSAVEL" to "Fulano", "CONTATO" to "fulano@exemplo.com")

    // ---------- parser ----------

    @Test
    fun tiposDeBlocoSaoReconhecidos() {
        val md = "# Título\n\n## Seção\nUm parágrafo.\n- item um\n  - item dois  \n"
        val b = Documento.blocos(md)
        assertEquals(
            listOf(
                Bloco(TipoBloco.TITULO, "Título"),
                Bloco(TipoBloco.SECAO, "Seção"),
                Bloco(TipoBloco.PARAGRAFO, "Um parágrafo."),
                Bloco(TipoBloco.ITEM, "item um"),
                Bloco(TipoBloco.ITEM, "item dois")
            ),
            b
        )
    }

    @Test
    fun tokensSaoSubstituidosECrasesRemovidas() {
        val b = Documento.blocos("Fale com {{CONTATO}} sobre `com.instagram.android`.", tokens)
        assertEquals("Fale com fulano@exemplo.com sobre com.instagram.android.", b.single().texto)
    }

    @Test
    fun tokenDesconhecidoNaoQuebra() {
        assertEquals("a {{OUTRO}} b", Documento.blocos("a {{OUTRO}} b", tokens).single().texto)
    }

    @Test
    fun negritoAlternaPelosPares() {
        assertEquals(
            listOf("antes " to false, "forte" to true, " depois" to false),
            Documento.trechos("antes **forte** depois")
        )
        assertEquals(listOf("sem negrito" to false), Documento.trechos("sem negrito"))
        // marcador sem par: o resto vira negrito, mas não lança exceção
        assertEquals(listOf("a " to false, "b" to true), Documento.trechos("a **b"))
    }

    // ---------- conteúdo dos documentos ----------

    private fun secoes(md: String) = Documento.blocos(md, tokens).filter { it.tipo == TipoBloco.SECAO }.map { it.texto }

    @Test
    fun documentosComecamComTituloEAindaTemTokensAntesDeSubstituir() {
        for (md in listOf(politica, termos)) {
            assertEquals(TipoBloco.TITULO, Documento.blocos(md, tokens).first().tipo)
            assertTrue(md.contains("{{RESPONSAVEL}}") && md.contains("{{CONTATO}}"))
        }
    }

    @Test
    fun depoisDeSubstituirNaoSobraTokenNemPlaceholder() {
        for (md in listOf(politica, termos)) {
            val texto = Documento.blocos(md, tokens).joinToString("\n") { it.texto }
            assertFalse("sobrou {{ }}", texto.contains("{{") || texto.contains("}}"))
            assertFalse("sobrou placeholder", texto.contains("[SEU") || texto.contains("TODO") || texto.contains("XXX"))
            assertTrue(texto.contains("fulano@exemplo.com"))
        }
    }

    @Test
    fun politicaTemAsSecoesEAsPromessasQueOAppCumpre() {
        val s = secoes(politica)
        listOf("Resumo", "O que o app acessa e por quê", "O que o app NÃO faz", "Consentimento e controle", "Crianças", "Seus direitos (LGPD)")
            .forEach { assertTrue("falta a seção '$it'", it in s) }
        val t = Documento.blocos(politica, tokens).joinToString("\n") { it.texto }
        assertTrue(t.contains("não tem permissão de acesso à internet"))
        assertTrue(t.contains("apenas eventos do Instagram"))
        assertTrue(t.contains("número de mensagens não lidas"))
        assertTrue(t.contains("Serviço de Acessibilidade"))
        assertTrue(t.contains("com.instagram.android"))
    }

    @Test
    fun termosTemAsSecoesEssenciais() {
        val s = secoes(termos)
        listOf("Aceitação", "O que é o app", "Sem afiliação", "Limitação de responsabilidade", "Lei aplicável", "Contato")
            .forEach { assertTrue("falta a seção '$it'", s.any { sec -> sec.contains(it) }) }
        val t = Documento.blocos(termos, tokens).joinToString("\n") { it.texto }
        assertTrue(t.contains("não tem afiliação"))
        assertTrue(t.contains("Política de Privacidade"))
        assertTrue(t.contains("como consumidor"))
    }

    @Test
    fun cadaLinhaDosDocumentosECurtaOSuficienteParaTela() {
        for (md in listOf(politica, termos)) {
            Documento.blocos(md, tokens).forEach { assertTrue("bloco longo demais", it.texto.length < 700) }
        }
    }
}
