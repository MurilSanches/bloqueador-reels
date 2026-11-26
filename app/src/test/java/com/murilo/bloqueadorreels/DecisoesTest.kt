package com.murilo.bloqueadorreels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisoesTest {

    @Test
    fun logoAposVoltarIgnoraParaNaoDarHomeSemQuerer() {
        assertEquals(AcaoReels.IGNORAR, Decisoes.acaoReels(0))
        assertEquals(AcaoReels.IGNORAR, Decisoes.acaoReels(Decisoes.IGNORAR_REPETICAO_MS - 1))
    }

    @Test
    fun reelsAindaAbertoDepoisDaEsperaUsaHome() {
        assertEquals(AcaoReels.HOME, Decisoes.acaoReels(Decisoes.IGNORAR_REPETICAO_MS))
        assertEquals(AcaoReels.HOME, Decisoes.acaoReels(Decisoes.USAR_HOME_ATE_MS - 1))
    }

    @Test
    fun reelsNovoMuitoDepoisVoltaDeNovo() {
        assertEquals(AcaoReels.VOLTAR, Decisoes.acaoReels(Decisoes.USAR_HOME_ATE_MS))
        assertEquals(AcaoReels.VOLTAR, Decisoes.acaoReels(Long.MAX_VALUE / 2))
    }

    @Test
    fun badgeSoAceitaNumeroCurto() {
        assertEquals("3", Decisoes.textoBadge("3"))
        assertEquals("99+", Decisoes.textoBadge(" 99+ "))
        assertEquals("", Decisoes.textoBadge(null))
        assertEquals("", Decisoes.textoBadge(""))
        // qualquer texto que não seja número vira só a bolinha: nunca repete conteúdo da tela
        assertEquals("", Decisoes.textoBadge("João mandou uma mensagem"))
        assertEquals("", Decisoes.textoBadge("12345"))
        assertEquals("", Decisoes.textoBadge("1;DROP"))
        assertEquals("", Decisoes.textoBadge("<b>3</b>"))
    }

    @Test
    fun abaDoInstagramGanhaDaUltimaTocada() {
        assertEquals("search", Decisoes.abaAtiva("search", "home"))
        assertEquals("profile", Decisoes.abaAtiva(null, "profile"))
    }

    @Test
    fun saudeComecaAContarNoPrimeiroUsoSemReconhecer() {
        assertEquals(1000L, Decisoes.semReconhecerDesde(0L, reconheceu = false, agora = 1000L, ultimoUso = 0L))
    }

    @Test
    fun saudeMantemInicioEnquantoNaoReconhece() {
        assertEquals(1000L, Decisoes.semReconhecerDesde(1000L, reconheceu = false, agora = 5000L, ultimoUso = 4000L))
    }

    @Test
    fun saudeZeraQuandoReconhece() {
        assertEquals(0L, Decisoes.semReconhecerDesde(1000L, reconheceu = true, agora = 5000L, ultimoUso = 4000L))
    }

    @Test
    fun saudeRecomecaDepoisDePausaLonga() {
        val agora = 10 * Decisoes.PAUSA_NOVA_SESSAO_MS
        val ultimoUso = agora - Decisoes.PAUSA_NOVA_SESSAO_MS - 1
        assertEquals(agora, Decisoes.semReconhecerDesde(1000L, reconheceu = false, agora = agora, ultimoUso = ultimoUso))
    }

    @Test
    fun avisaSoDepoisDeUsoLongoSemReconhecer() {
        val inicio = 1_000L
        assertFalse(Decisoes.avisarBarraNaoReconhecida(0L, inicio + Decisoes.LIMITE_SEM_RECONHECER_MS * 2))
        assertFalse(Decisoes.avisarBarraNaoReconhecida(inicio, inicio + Decisoes.LIMITE_SEM_RECONHECER_MS))
        assertTrue(Decisoes.avisarBarraNaoReconhecida(inicio, inicio + Decisoes.LIMITE_SEM_RECONHECER_MS + 1))
    }
}
