package com.murilo.bloqueadorreels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssinaturasTest {

    private val assin: Assinaturas = run {
        val arquivo = File("src/main/assets/assinaturas.json")
        Assinaturas.deJson(arquivo.readText())
    }

    @Test
    fun jsonCarregaTodosOsBotoes() {
        val chaves = assin.botoes.map { it.chave }
        assertTrue(chaves.containsAll(listOf("home", "search", "create", "reels", "profile")))
        assertTrue(assin.containerBarraIds.isNotEmpty())
        assertTrue(assin.reelsViewerIds.isNotEmpty())
    }

    @Test
    fun reconhecePorId() {
        assertEquals("reels", assin.identificar("com.instagram.android:id/clips_tab", null))
        assertEquals("home", assin.identificar("com.instagram.android:id/feed_tab", "qualquer"))
    }

    @Test
    fun reconhecePorDescricaoNosDoisIdiomas() {
        assertEquals("home", assin.identificar(null, "Home"))
        assertEquals("home", assin.identificar(null, "Início"))
        assertEquals("search", assin.identificar(null, "Search and explore"))
        assertEquals("reels", assin.identificar(null, "Reels"))
        assertEquals("profile", assin.identificar(null, "Perfil"))
    }

    @Test
    fun descricoesReaisDoInstagramEmPortuguesEIngles() {
        // Medidas no emulador (2026-11-25), sem usar o ID.
        val reais = mapOf(
            "Página inicial" to "home", "Home" to "home",
            "Mensagem" to "messages", "Message" to "messages",
            "Pesquisar e explorar" to "search", "Search and explore" to "search",
            "Perfil" to "profile", "Profile" to "profile",
            "Reels" to "reels"
        )
        reais.forEach { (desc, chave) -> assertEquals(desc, chave, assin.identificar(null, desc)) }
    }

    // ---------- Segurança: arquivo de assinaturas adulterado / inválido ----------

    private val validoTexto = File("src/main/assets/assinaturas.json").readText()

    @Test
    fun arquivoDoAppPassaNaValidacao() {
        assertTrue(Assinaturas.deJsonOuNull(validoTexto) != null)
    }

    @Test
    fun idsDoReelsDeChatVemDoArquivoEPrecisamSerDoInstagram() {
        assertTrue(assin.reelsChatIds.isNotEmpty())
        val adulterado = validoTexto.replace(
            "com.instagram.android:id/sender_timestamp", "com.banco.app:id/saldo"
        )
        assertNull(Assinaturas.deJsonOuNull(adulterado))
    }

    @Test
    fun idsDeReservaVemDoArquivoEPrecisamSerDoInstagram() {
        assertTrue(assin.reelsReservaIds.size >= Decisoes.MIN_RESERVA)
        val adulterado = validoTexto.replace(assin.reelsReservaIds.first(), "com.banco.app:id/saldo")
        assertNull(Assinaturas.deJsonOuNull(adulterado))
    }

    @Test
    fun listaDeReservaGrandeDemaisERecusada() {
        val ids = (1..5).joinToString(",") { "\"com.instagram.android:id/r$it\"" }
        val adulterado = validoTexto.replace(Regex("\"reelsReservaIds\": \\[[^]]*]"), "\"reelsReservaIds\": [$ids]")
        assertNull(Assinaturas.deJsonOuNull(adulterado))
    }

    @Test
    fun arquivoSemListaDeReservaAindaCarrega() {
        val sem = validoTexto.replace(Regex("\"reelsReservaIds\": \\[[^]]*],?"), "")
        val carregado = Assinaturas.deJsonOuNull(sem)
        assertTrue(carregado != null)
        assertTrue(carregado!!.reelsReservaIds.isEmpty())
    }

    @Test
    fun jsonMalformadoOuVazioViraNullSemExcecao() {
        listOf("", "   ", "{", "[]", "null", "not json", "{\"botoes\": 5}", "{\"botoes\": [1,2]}")
            .forEach { assertNull(it, Assinaturas.deJsonOuNull(it)) }
    }

    @Test
    fun arquivoGrandeDemaisERecusado() {
        val enorme = validoTexto + " ".repeat((Assinaturas.MAX_BYTES_ARQUIVO + 1).toInt())
        assertNull(Assinaturas.deJsonOuNull(enorme))
    }

    @Test
    fun idDeOutroAppERecusado() {
        val adulterado = validoTexto.replace("com.instagram.android:id/clips_tab", "com.banco.app:id/saldo")
        assertNull(Assinaturas.deJsonOuNull(adulterado))
        val container = validoTexto.replace("com.instagram.android:id/tab_bar", "android:id/content")
        assertNull(Assinaturas.deJsonOuNull(container))
    }

    @Test
    fun semBotaoReelsOuSemContainerERecusado() {
        assertNull(Assinaturas.deJsonOuNull(validoTexto.replace("\"chave\": \"reels\"", "\"chave\": \"outro\"")))
        assertNull(Assinaturas.deJsonOuNull(validoTexto.replace(Regex("\"containerBarraIds\": \\[[^]]*]"), "\"containerBarraIds\": []")))
        assertNull(Assinaturas.deJsonOuNull(validoTexto.replace(Regex("\"reelsViewerIds\": \\[[^]]*]"), "\"reelsViewerIds\": []")))
    }

    @Test
    fun listasEnormesOuStringsGigantesSaoRecusadas() {
        val muitosBotoes = (1..30).joinToString(",") {
            "{\"chave\":\"b$it\",\"ids\":[],\"descricoes\":[\"x\"]}"
        }
        val json = "{\"versao\":1,\"containerBarraIds\":[\"com.instagram.android:id/tab_bar\"]," +
            "\"reelsViewerIds\":[\"com.instagram.android:id/v\"],\"botoes\":[" +
            "{\"chave\":\"reels\",\"ids\":[],\"descricoes\":[\"Reels\"]},$muitosBotoes]}"
        assertNull(Assinaturas.deJsonOuNull(json))
        val longo = "a".repeat(500)
        val stringGigante = validoTexto.replace("\"Reels\"", "\"$longo\"")
        assertNull(Assinaturas.deJsonOuNull(stringGigante))
    }

    @Test
    fun descricaoComCaracteresEstranhosNaoQuebraOCasamento() {
        assertNull(assin.identificar(null, "\u0000￿\n\t".repeat(1000)))
        assertNull(assin.identificar("%s%n${'$'}{x}", "'; DROP TABLE;--"))
    }

    @Test
    fun descricaoComSufixoDeNotificacaoAindaBate() {
        assertEquals("home", assin.identificar(null, "Home, 3 novas"))
        assertEquals("profile", assin.identificar(null, "  PROFILE "))
    }

    @Test
    fun naoBateComTextoParecido() {
        assertNull(assin.identificar(null, "Homestead"))
        assertNull(assin.identificar(null, "Reelsfoo"))
        assertNull(assin.identificar(null, null))
        assertNull(assin.identificar("com.outro:id/x", ""))
    }
}
