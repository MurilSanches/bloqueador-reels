package com.murilo.bloqueadorreels

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private var servicoAtivo by mutableStateOf(false)
    private var barraNaoReconhecida by mutableStateOf(false)
    private var temaApp by mutableStateOf(Prefs.TEMA_AUTO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        temaApp = Prefs.temaApp(this)
        setContent {
            // Modo escuro do app: segue o celular até o usuário mexer no interruptor.
            val sistemaEscuro = isSystemInDarkTheme()
            val escuro = when (temaApp) {
                Prefs.TEMA_ESCURO -> true
                Prefs.TEMA_CLARO -> false
                else -> sistemaEscuro
            }
            // Ícones da barra de status/navegação acompanham o tema escolhido (não só o do sistema).
            SideEffect {
                val estilo = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { escuro }
                enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
            }
            AppTheme(escuro) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var documento by remember { mutableStateOf<DocumentoLegal?>(null) }
                    BackHandler(enabled = documento != null) { documento = null }
                    val aberto = documento
                    if (aberto != null) TelaDocumento(aberto) { documento = null } else Column(
                        Modifier
                            .safeDrawingPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        var consentido by remember { mutableStateOf(Prefs.consentimento(this@MainActivity)) }
                        if (!consentido) {
                            TelaAviso(
                                onAceitar = {
                                    Prefs.setConsentimento(this@MainActivity, true)
                                    consentido = true
                                },
                                // Recusar: nada é ativado e o app fecha. O serviço continua inerte
                                // (sem consentimento ele não faz nada), e o aviso volta na próxima abertura.
                                onRecusar = { finish() },
                                abrirDocumento = { documento = it }
                            )
                        } else {
                            TelaPrincipal(
                                servicoAtivo = servicoAtivo,
                                barraNaoReconhecida = barraNaoReconhecida,
                                escuro = escuro,
                                onEscuro = {
                                    val novo = if (it) Prefs.TEMA_ESCURO else Prefs.TEMA_CLARO
                                    temaApp = novo
                                    Prefs.setTemaApp(this@MainActivity, novo)
                                },
                                abrirAcessibilidade = {
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                },
                                abrirDocumento = { documento = it }
                            )
                        }
                    }
                }
            }
        }
    }

    // O usuário volta das Configurações de Acessibilidade: reavalia o status.
    override fun onResume() {
        super.onResume()
        servicoAtivo = servicoAcessibilidadeAtivo(this)
        barraNaoReconhecida = Decisoes.avisarBarraNaoReconhecida(
            Prefs.semReconhecerDesde(this), Prefs.ultimoUsoInstagram(this)
        )
    }
}

/** Compara o serviço com `ENABLED_ACCESSIBILITY_SERVICES` (aceita nome curto ou completo). */
fun servicoAcessibilidadeAtivo(ctx: Context): Boolean {
    val habilitados = Settings.Secure.getString(
        ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val alvo = ComponentName(ctx, ReelsBlockerService::class.java)
    return habilitados.split(':').any { ComponentName.unflattenFromString(it) == alvo }
}

@Composable
private fun AppTheme(escuro: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val cores = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (escuro) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        escuro -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = cores, content = content)
}

// ---------- Aviso / consentimento ----------

@Composable
private fun TelaAviso(
    onAceitar: () -> Unit,
    onRecusar: () -> Unit,
    abrirDocumento: (DocumentoLegal) -> Unit
) {
    Spacer(Modifier.height(8.dp))
    Text("Antes de ativar", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
    Text(
        "O app usa o Serviço de Acessibilidade do Android. Veja o que isso significa:",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ItemAviso("O que ele lê", "Só a tela do Instagram: os botões da barra de baixo, o número de mensagens não lidas do botão de mensagens (só o número, para mostrá-lo na barra, como o Instagram faz) e se a tela de Reels está aberta. O Android entrega ao serviço apenas os eventos do Instagram. Para saber se o Instagram ainda está na tela, o app confere só o nome do app que está na frente, sem ler o conteúdo dele. Não lê mensagens, senhas nem textos digitados.")
    ItemAviso("Por que lê", "Para esconder o botão de Reels, desenhando uma barra própria por cima, e para voltar quando o Reels abrir por outro caminho.")
    ItemAviso("Para onde vai", "Nada sai do celular. O app não tem permissão de internet e não guarda o que lê.")
    ItemAviso("Controle", "Você desliga o serviço quando quiser em Configurações › Acessibilidade.")
    Button(onClick = onAceitar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Entendi e aceito")
    }
    OutlinedButton(onClick = onRecusar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Não aceito")
    }
    RodapeLegal(abrirDocumento)
}

@Composable
private fun ItemAviso(titulo: String, texto: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(texto, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ---------- Tela principal ----------

@Composable
private fun TelaPrincipal(
    servicoAtivo: Boolean,
    barraNaoReconhecida: Boolean,
    escuro: Boolean,
    onEscuro: (Boolean) -> Unit,
    abrirAcessibilidade: () -> Unit,
    abrirDocumento: (DocumentoLegal) -> Unit
) {
    val ctx = LocalContext.current
    // Um só controle para o bloqueio: o cartão de status e a prévia leem o mesmo estado.
    var bloquear by remember { mutableStateOf(Prefs.ocultarReels(ctx)) }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Bloqueador de Reels", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Instagram sem a aba Reels",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    CartaoStatus(
        servicoAtivo = servicoAtivo,
        bloquear = bloquear,
        onBloquear = {
            bloquear = it
            Prefs.setOcultarReels(ctx, it)
        },
        abrirAcessibilidade = abrirAcessibilidade
    )
    if (servicoAtivo && barraNaoReconhecida) CartaoAlerta()

    Secao("Barra de baixo do Instagram")
    CartaoPrevia(semReels = servicoAtivo && bloquear)

    Secao("Aparência")
    CartaoModoEscuro(escuro, onEscuro)

    // Só em build debug: despeja a tela do Instagram (nomes, legendas) no Logcat.
    if (Prefs.ehDebug(ctx)) {
        Secao("Desenvolvimento")
        CartaoMapeamento(ctx)
    }

    Text(
        "Tudo roda no seu celular. O app não tem acesso à internet.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
    RodapeLegal(abrirDocumento)
}

/** Documentos legais exibidos dentro do app (a Play exige a política também no app). */
enum class DocumentoLegal(val titulo: String, val arquivo: String) {
    POLITICA("Política de privacidade", "politica-de-privacidade.md"),
    TERMOS("Termos de uso", "termos-de-uso.md")
}

@Composable
private fun RodapeLegal(abrir: (DocumentoLegal) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { abrir(DocumentoLegal.POLITICA) }) { Text(DocumentoLegal.POLITICA.titulo) }
            TextButton(onClick = { abrir(DocumentoLegal.TERMOS) }) { Text(DocumentoLegal.TERMOS.titulo) }
        }
        Text(
            "App independente, sem afiliação com o Instagram ou a Meta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TelaDocumento(documento: DocumentoLegal, onVoltar: () -> Unit) {
    val ctx = LocalContext.current
    // Vazio (antes de publicar): remete à página do app na Google Play.
    val responsavel = stringResource(R.string.responsavel_nome)
        .ifBlank { "o desenvolvedor do app (veja a página do app na Google Play)" }
    val contato = stringResource(R.string.contato_email).ifBlank { "pela página do app na Google Play" }
    val blocos = remember(documento, responsavel, contato) {
        val texto = ctx.assets.open(documento.arquivo).bufferedReader().use { it.readText() }
        Documento.blocos(texto, mapOf("RESPONSAVEL" to responsavel, "CONTATO" to contato))
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onVoltar) { Text("‹ Voltar") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            blocos.forEach { b ->
                when (b.tipo) {
                    TipoBloco.TITULO -> Text(b.texto, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    TipoBloco.SECAO -> Text(
                        b.texto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp)
                    )
                    TipoBloco.PARAGRAFO -> Text(comNegrito(b.texto), style = MaterialTheme.typography.bodyMedium)
                    TipoBloco.ITEM -> Row {
                        Text("•  ", style = MaterialTheme.typography.bodyMedium)
                        Text(comNegrito(b.texto), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun comNegrito(texto: String): AnnotatedString = buildAnnotatedString {
    Documento.trechos(texto).forEach { (parte, negrito) ->
        if (negrito) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(parte) } else append(parte)
    }
}

@Composable
private fun Secao(titulo: String) {
    Text(
        titulo,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun CartaoStatus(
    servicoAtivo: Boolean,
    bloquear: Boolean,
    onBloquear: (Boolean) -> Unit,
    abrirAcessibilidade: () -> Unit
) {
    // Três estados claros e sem contradição: o título e o texto dizem o que está acontecendo AGORA.
    val cores = MaterialTheme.colorScheme
    val (fundo, texto, ponto) = when {
        !servicoAtivo -> Triple(cores.errorContainer, cores.onErrorContainer, cores.error)
        bloquear -> Triple(cores.primaryContainer, cores.onPrimaryContainer, Color(0xFF2E9E5B))
        else -> Triple(cores.surfaceVariant, cores.onSurfaceVariant, cores.outline)
    }
    val titulo = when {
        !servicoAtivo -> "Serviço desativado"
        bloquear -> "Bloqueio ativado"
        else -> "Bloqueio pausado"
    }
    val descricao = when {
        !servicoAtivo -> "Ative o \"Bloqueador de Reels\" em Configurações › Acessibilidade para começar."
        bloquear -> "A aba Reels está escondida no Instagram, e o Reels volta sozinho se abrir por vídeo, link ou mensagem."
        else -> "O Instagram está normal, com a aba Reels. Ligue o interruptor abaixo para esconder."
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = fundo, contentColor = texto)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(ponto))
                Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Text(descricao, style = MaterialTheme.typography.bodyMedium)
            if (!servicoAtivo) {
                Button(onClick = abrirAcessibilidade, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Abrir Acessibilidade")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        "Bloquear Reels",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = bloquear, onCheckedChange = onBloquear)
                }
            }
        }
    }
}

@Composable
private fun CartaoAlerta() {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Barra do Instagram não reconhecida", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Você usou o Instagram por um bom tempo e o app não achou a barra de baixo nem a tela de Reels. " +
                    "Pode ser uma versão nova do Instagram. Se o Reels apareceu, atualize este app.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CartaoPrevia(semReels: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            PreviewBarra(semReels = semReels)
        }
    }
}

/** Miniatura da barra do Instagram, com ou sem o Reels, para mostrar o efeito do interruptor. */
@Composable
private fun PreviewBarra(semReels: Boolean) {
    // (contorno, preenchido): o primeiro item é o ativo, como no Instagram.
    val itens = buildList {
        add(R.drawable.ic_nav_home to R.drawable.ic_nav_home_on)
        if (!semReels) add(R.drawable.ic_reels_preview to R.drawable.ic_reels_preview)
        add(R.drawable.ic_nav_messages to R.drawable.ic_nav_messages_on)
        add(R.drawable.ic_nav_search to R.drawable.ic_nav_search_on)
        add(R.drawable.ic_nav_profile to R.drawable.ic_nav_profile_on)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (semReels) "Como fica" else "Como está agora",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            itens.forEachIndexed { i, (contorno, cheio) ->
                IconeBarra(if (i == 0) cheio else contorno)
            }
        }
    }
}

@Composable
private fun IconeBarra(@DrawableRes id: Int) {
    Image(
        painter = painterResource(id),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        // Como no Instagram: mesma cor em todos; ativo = preenchido.
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
    )
}

@Composable
private fun CartaoModoEscuro(escuro: Boolean, onEscuro: (Boolean) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Modo escuro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Só muda a aparência deste app. A barra no Instagram sempre acompanha o tema do celular.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(12.dp))
            Switch(checked = escuro, onCheckedChange = onEscuro)
        }
    }
}

@Composable
private fun CartaoMapeamento(ctx: Context) {
    var ligado by remember { mutableStateOf(Prefs.mapeamento(ctx)) }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Modo de mapeamento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Despeja a árvore de views do Instagram no Logcat (tag MAPA). Deixe desligado no uso normal: deixa o Instagram lento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(12.dp))
            Switch(checked = ligado, onCheckedChange = {
                ligado = it
                Prefs.setMapeamento(ctx, it)
            })
        }
    }
}
