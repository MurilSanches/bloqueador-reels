# Ciclo 1 — `BarraOverlay`, `AppAlvo` e sinal reserva: plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tirar o desenho da barra e a detecção de Reels de dentro do `ReelsBlockerService`, deixando-os genéricos por plataforma, e acrescentar um sinal reserva que também bloqueia Reels, sem mudar o que o usuário vê.

**Architecture:** `BarraOverlay` desenha a barra substituta a partir de um `EstadoBarra` e de um `EstiloBarra` (ícones, descrições, cores) fornecido por cada plataforma. `AppAlvo` (interface) responde "qual pacote, como ler a barra, essa tela é vídeo curto?"; `InstagramAlvo` é a única implementação agora. O sinal reserva é uma função pura em `Decisoes` (testada na JVM) alimentada por IDs medidos no emulador e guardados em `assinaturas.json`.

**Tech Stack:** Kotlin, Android `AccessibilityService`, JUnit 4 (JVM), Gradle 9.3.1 / AGP 9.1.1, emulador `Medium_Phone_API_37.0`.

Spec: `planejamento/specs/2026-11-27-ciclo1-overlay-e-app-alvo-design.md`

## Global Constraints

- Comportamento visível idêntico ao atual: bloqueio por `BACK`→`HOME`→`BACK` (`Decisoes.acaoReels`), barra sem piscar, toque longo, badges, tema claro/escuro, link/chat.
- `desenharBarra`/`mostrar` nunca fazem remove+add em atualização simples (só se bounds, tema ou botões mudarem).
- Sem permissão de internet. Serviço só com `android:packageNames="com.instagram.android"`. `accessibility_config.xml` não muda.
- `Assinaturas.valida()` em todo JSON: IDs só com prefixo `com.instagram.android:id/` (inclusive os novos `reelsReservaIds`).
- Nenhum texto/nome da tela é lido ou guardado; só presença de view IDs.
- Código e comentários em pt-BR, como o resto do projeto. Os 32 testes atuais continuam passando.
- Commits: um por tarefa, com a linha final `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Dumps brutos do Instagram (feed real) **nunca** vão para o git.

### Comandos usados no plano

Build/teste (Git Bash, na raiz `D:\projetos\bloqueador-reels`):

```bash
export JAVA_HOME="C:\Program Files\Unity\Hub\Editor\6000.6.2f1\Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK"
./gradlew.bat testDebugUnitTest --no-daemon          # só testes
./gradlew.bat testDebugUnitTest assembleDebug --no-daemon
./gradlew.bat lintRelease --no-daemon
```

adb (Git Bash): `export PATH="$LOCALAPPDATA/Android/Sdk/platform-tools:$PATH"` e, para screenshot, `MSYS_NO_PATHCONV=1 adb exec-out screencap -p > arq.png`.

## Estrutura de arquivos

| Arquivo | Ação | Responsabilidade |
|---|---|---|
| `app/.../Decisoes.kt` | Modifica | `MIN_RESERVA`, `telaVideoCurto(principalVisivel, reservaVisiveis)` |
| `app/.../Assinaturas.kt` | Modifica | campo `reelsReservaIds` (leitura, validação, limite) |
| `app/src/main/assets/assinaturas.json` | Modifica | IDs de reserva medidos |
| `app/.../AppAlvo.kt` | Cria | interfaces `AppAlvo` e `EstiloBarra` |
| `app/.../InstagramAlvo.kt` | Cria | `InstagramAlvo` (pacote, leitura, detecção) e `InstagramEstilo` (ícones, cores) |
| `app/.../BarraOverlay.kt` | Cria | janela e views da barra substituta |
| `app/.../ReelsBlockerService.kt` | Modifica | usa `InstagramAlvo` e `BarraOverlay`; perde ~250 linhas de UI |
| `app/src/test/.../DecisoesTest.kt`, `AssinaturasTest.kt` | Modifica | testes novos |
| `planejamento/medicoes/` | Cria | lista de IDs medidos (dumps brutos ignorados pelo git) |
| `CLAUDE.md`, `playstore/declaracao-acessibilidade.md` | Modifica | estrutura nova e frase da declaração |

---

### Task 1: Medir os IDs de reserva no emulador (portão de decisão)

**Files:**
- Create: `planejamento/medicoes/2026-11-27-reels-ids.md`
- Modify: `.gitignore`
- Modify: `planejamento/specs/2026-11-27-ciclo1-overlay-e-app-alvo-design.md` (seção "Sinal reserva")

**Interfaces:**
- Produces: lista de 2 a 4 IDs `com.instagram.android:id/...` visíveis só na tela de Reels (usada na Task 3), ou a decisão de parar.

Observação: o spec dizia "`tab_bar` ausente **e** ID de reserva visível". Isso excluiria o Reels aberto pela aba Reels, onde a barra do Instagram continua visível. Esta tarefa troca a segunda condição por "**pelo menos 2** IDs de reserva visíveis juntos" e corrige o spec.

- [ ] **Step 1: Ignorar dumps brutos no git**

Acrescente ao fim de `.gitignore`:

```
# Dumps da árvore do Instagram contêm feed real e contas: nunca versionar
planejamento/medicoes/dump-*.txt
planejamento/medicoes/ids-*.txt
```

- [ ] **Step 2: Subir o emulador e instalar o debug**

```bash
export JAVA_HOME="C:\Program Files\Unity\Hub\Editor\6000.6.2f1\Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK"
export PATH="$LOCALAPPDATA/Android/Sdk/platform-tools:$PATH"
./gradlew.bat assembleDebug --no-daemon
adb devices
```

Se `adb devices` não listar um emulador, inicie: `"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd Medium_Phone_API_37.0 &` e espere `adb shell getprop sys.boot_completed` devolver `1`.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put secure enabled_accessibility_services com.murilo.bloqueadorreels/com.murilo.bloqueadorreels.ReelsBlockerService
```

Abra o app (`adb shell monkey -p com.murilo.bloqueadorreels 1`) e, se aparecer o aviso, toque em "Aceito" (tire screenshot para achar o botão; `adb shell input tap X Y`). Na tela principal ligue **"Modo de mapeamento"** e **"Reels por link"** (o mapeamento só existe em build debug; o link mantém o Reels aberto para ser medido).
Expected: com o Instagram logado no emulador, o app mostra o serviço ativo.

- [ ] **Step 3: Dump da tela de Reels (entrada por link)**

```bash
cd planejamento/medicoes
adb logcat -c
adb shell am force-stop com.instagram.android
adb shell am start -a android.intent.action.VIEW -d instagram://reels_home -p com.instagram.android
sleep 6
adb logcat -d -s MAPA > dump-reels-link.txt
grep "vis=true" dump-reels-link.txt | grep -o "id=com.instagram.android:id/[A-Za-z0-9_]*" | sort -u > ids-reels.txt
wc -l ids-reels.txt
```
Expected: `ids-reels.txt` com algumas dezenas de IDs. Se vier vazio, o Reels foi bloqueado antes do dump: confirme que "Reels por link" está ligado.

- [ ] **Step 4: Dump das telas que NÃO são Reels**

Para cada tela abaixo, abra-a, espere 3 s e repita o padrão (troque o nome do arquivo): feed (abrir o Instagram pelo launcher), Explorar, Perfil, Mensagens (lista) e uma conversa. Navegue tocando na nossa barra (tire screenshot para achar as coordenadas).

```bash
adb logcat -c
# ...navegue até a tela...
sleep 3
adb logcat -d -s MAPA > dump-TELA.txt
grep "vis=true" dump-TELA.txt | grep -o "id=com.instagram.android:id/[A-Za-z0-9_]*" | sort -u > ids-TELA.txt
```
(substitua `TELA` por `feed`, `explorar`, `perfil`, `mensagens`, `conversa`).

- [ ] **Step 5: Calcular os IDs exclusivos do Reels**

```bash
cat ids-feed.txt ids-explorar.txt ids-perfil.txt ids-mensagens.txt ids-conversa.txt | sort -u > ids-outras.txt
comm -23 ids-reels.txt ids-outras.txt
```
Expected: lista de IDs só do Reels (ex.: nomes com `clips_`). Escolha de 2 a 4 deles, preferindo nomes de funcionalidade (`clips_...`) a nomes genéricos, e **excluindo** `clips_viewer_view_pager` (já é o principal) e `clips_tab`.

**Portão:** se sobrarem **menos de 2** IDs exclusivos, PARE. Registre isso no arquivo de medição, não implemente as Tasks 3 a 4 de reserva e avise o Murilo: o reserva passa a valer só como aviso de saúde (conforme o risco previsto no spec).

- [ ] **Step 6: Registrar a medição**

Crie `planejamento/medicoes/2026-11-27-reels-ids.md`:

```markdown
# IDs de reserva do Reels (medido em 2026-11-27)

Emulador `Medium_Phone_API_37.0`, Instagram em <versão do app>, idioma <idioma>.
Medido por entrada via `instagram://reels_home` com "Reels por link" ligado.
Comparado com: feed, Explorar, Perfil, Mensagens, conversa.

IDs só do Reels escolhidos para `reelsReservaIds`:

- com.instagram.android:id/<ID 1>
- com.instagram.android:id/<ID 2>
(- até 4)

Regra: vale como tela de Reels se ≥ 2 destes IDs estão visíveis ao mesmo tempo.
Limitação: Reels aberto pela aba Reels e por Explorar não foi medido (a aba fica oculta
pela nossa barra); assume-se o mesmo visualizador.
```
Preencha `<versão do app>` (`adb shell dumpsys package com.instagram.android | grep versionName`), `<idioma>` e os IDs reais.

- [ ] **Step 7: Corrigir o spec**

Em `planejamento/specs/2026-11-27-ciclo1-overlay-e-app-alvo-design.md`, substitua a seção "### Sinal reserva" inteira por:

```markdown
### Sinal reserva
`telaVideoCurto` devolve não nulo se:
- o ID principal (`reelsViewerIds`) está visível; **ou**
- pelo menos `Decisoes.MIN_RESERVA` (2) IDs de `reelsReservaIds` (novo campo em `assinaturas.json`, no máximo 4) estão visíveis ao mesmo tempo.

Dois IDs independentes juntos reduzem o falso positivo. Não se usa a ausência de `tab_bar` como condição: o Reels aberto pela aba Reels mantém a barra visível. `reelsReservaIds` passa por `Assinaturas.valida()` (só IDs `com.instagram.android:id/`). A regra de combinação é função pura em `Decisoes.kt`, testada na JVM. Os IDs vêm de `planejamento/medicoes/2026-11-27-reels-ids.md`.
```
Em "Riscos", troque "mitigado pelos dois sinais" por "mitigado por exigir dois IDs exclusivos do Reels juntos".

- [ ] **Step 8: Commit**

```bash
git add .gitignore planejamento/medicoes/2026-11-27-reels-ids.md planejamento/specs/2026-11-27-ciclo1-overlay-e-app-alvo-design.md
git commit -m "Mede IDs de reserva do Reels e ajusta a regra do sinal reserva no spec

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
git status --short
```
Expected: nenhum `dump-*.txt`/`ids-*.txt` em `git status`.

---

### Task 2: Regra pura do sinal reserva em `Decisoes`

**Files:**
- Modify: `app/src/main/java/com/murilo/bloqueadorreels/Decisoes.kt`
- Test: `app/src/test/java/com/murilo/bloqueadorreels/DecisoesTest.kt`

**Interfaces:**
- Produces: `Decisoes.MIN_RESERVA: Int` (= 2) e `Decisoes.telaVideoCurto(principalVisivel: Boolean, reservaVisiveis: Int): Boolean`.

- [ ] **Step 1: Escrever os testes que falham**

Em `DecisoesTest.kt`, dentro da classe, depois de `reelsNovoMuitoDepoisVoltaDeNovo`:

```kotlin
    @Test
    fun telaVideoCurtoPeloIdPrincipal() {
        assertTrue(Decisoes.telaVideoCurto(principalVisivel = true, reservaVisiveis = 0))
    }

    @Test
    fun telaVideoCurtoPeloReservaExigeTodosOsSinaisJuntos() {
        assertTrue(Decisoes.telaVideoCurto(false, Decisoes.MIN_RESERVA))
        assertTrue(Decisoes.telaVideoCurto(false, Decisoes.MIN_RESERVA + 1))
    }

    @Test
    fun umSinalDeReservaSozinhoNaoBasta() {
        assertFalse(Decisoes.telaVideoCurto(false, Decisoes.MIN_RESERVA - 1))
        assertFalse(Decisoes.telaVideoCurto(false, 0))
    }
```

- [ ] **Step 2: Rodar e ver falhar**

```bash
./gradlew.bat testDebugUnitTest --tests "com.murilo.bloqueadorreels.DecisoesTest" --no-daemon
```
Expected: FALHA de compilação `Unresolved reference: telaVideoCurto` / `MIN_RESERVA`.

- [ ] **Step 3: Implementar**

Em `Decisoes.kt`, depois de `fun acaoReels(...)`:

```kotlin
    /** Quantos IDs de reserva precisam estar visíveis juntos para valer como tela de vídeo curto. */
    const val MIN_RESERVA = 2

    /**
     * A tela é de vídeo curto (Reels)? Vale o ID principal; se ele sumir numa versão nova do
     * Instagram, valem os IDs de reserva, mas só juntos (um sozinho poderia aparecer em outra tela).
     */
    fun telaVideoCurto(principalVisivel: Boolean, reservaVisiveis: Int): Boolean =
        principalVisivel || reservaVisiveis >= MIN_RESERVA
```

- [ ] **Step 4: Rodar e ver passar**

Mesmo comando do Step 2.
Expected: `BUILD SUCCESSFUL`, `DecisoesTest` verde.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/murilo/bloqueadorreels/Decisoes.kt app/src/test/java/com/murilo/bloqueadorreels/DecisoesTest.kt
git commit -m "Decisoes.telaVideoCurto: ID principal ou IDs de reserva juntos

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 3: `reelsReservaIds` em `Assinaturas` e no JSON

**Files:**
- Modify: `app/src/main/java/com/murilo/bloqueadorreels/Assinaturas.kt`
- Modify: `app/src/main/assets/assinaturas.json`
- Test: `app/src/test/java/com/murilo/bloqueadorreels/AssinaturasTest.kt`

**Interfaces:**
- Consumes: `Decisoes.MIN_RESERVA` (Task 2); IDs de `planejamento/medicoes/2026-11-27-reels-ids.md` (Task 1).
- Produces: `Assinaturas.reelsReservaIds: List<String>` (padrão vazio; no máximo 4; todos com prefixo do Instagram).

- [ ] **Step 1: Escrever os testes que falham**

Em `AssinaturasTest.kt`, depois de `idsDoReelsDeChatVemDoArquivoEPrecisamSerDoInstagram`:

```kotlin
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
```

- [ ] **Step 2: Rodar e ver falhar**

```bash
./gradlew.bat testDebugUnitTest --tests "com.murilo.bloqueadorreels.AssinaturasTest" --no-daemon
```
Expected: FALHA de compilação `Unresolved reference: reelsReservaIds`.

- [ ] **Step 3: Implementar em `Assinaturas.kt`**

(a) No construtor, depois de `reelsChatIds`, troque

```kotlin
    val reelsChatIds: List<String> = emptyList()
) {
```
por
```kotlin
    val reelsChatIds: List<String> = emptyList(),
    /**
     * IDs de reserva do Reels aberto: só valem juntos (`Decisoes.telaVideoCurto`), para detectar a
     * tela quando o ID principal mudar numa versão nova do Instagram. Só a presença é conferida.
     */
    val reelsReservaIds: List<String> = emptyList()
) {
```

(b) Em `valida()`, troque

```kotlin
        if (badgeIds.size > MAX_IDS || reelsChatIds.size > MAX_IDS) return false
```
por
```kotlin
        if (badgeIds.size > MAX_IDS || reelsChatIds.size > MAX_IDS) return false
        if (reelsReservaIds.size > MAX_RESERVA) return false
```
e troque
```kotlin
        val ids = containerBarraIds + reelsViewerIds + badgeIds + reelsChatIds + botoes.flatMap { it.ids }
```
por
```kotlin
        val ids = containerBarraIds + reelsViewerIds + badgeIds + reelsChatIds + reelsReservaIds +
            botoes.flatMap { it.ids }
```

(c) No `companion object`, depois de `private const val MAX_IDS = 8`:

```kotlin
        /** Cada ID de reserva custa uma consulta ao Instagram a cada leitura: poucos. */
        private const val MAX_RESERVA = 4
```

(d) Em `deJson`, troque
```kotlin
                reelsChatIds = o.optJSONArray("reelsChatIds").paraLista()
            )
```
por
```kotlin
                reelsChatIds = o.optJSONArray("reelsChatIds").paraLista(),
                reelsReservaIds = o.optJSONArray("reelsReservaIds").paraLista()
            )
```

- [ ] **Step 4: Acrescentar os IDs ao JSON**

Em `assinaturas.json`, depois do bloco `"reelsChatIds": [...],` e antes de `"badgeIds"`, insira (com os IDs reais da medição da Task 1, 2 a 4, todos começando com `com.instagram.android:id/`):

```json
  "reelsReservaIds": [
    "com.instagram.android:id/<ID 1 medido>",
    "com.instagram.android:id/<ID 2 medido>"
  ],
```

- [ ] **Step 5: Rodar todos os testes**

```bash
./gradlew.bat testDebugUnitTest --no-daemon
```
Expected: `BUILD SUCCESSFUL`; `AssinaturasTest` e `DecisoesTest` verdes, nenhum teste antigo quebrado.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/murilo/bloqueadorreels/Assinaturas.kt app/src/main/assets/assinaturas.json app/src/test/java/com/murilo/bloqueadorreels/AssinaturasTest.kt
git commit -m "Assinaturas: reelsReservaIds (validados, no máximo 4)

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 4: `AppAlvo` + `InstagramAlvo` e o serviço passa a usá-los

**Files:**
- Create: `app/src/main/java/com/murilo/bloqueadorreels/AppAlvo.kt`
- Create: `app/src/main/java/com/murilo/bloqueadorreels/InstagramAlvo.kt`
- Modify: `app/src/main/java/com/murilo/bloqueadorreels/ReelsBlockerService.kt`

**Interfaces:**
- Consumes: `Assinaturas` (com `reelsReservaIds`), `Decisoes.telaVideoCurto`, `BarraReader.ler`, `EstadoBarra`, `LeituraBarra`.
- Produces:
  - `interface EstiloBarra { fun icone(chave: String, ativo: Boolean): Int; fun descricao(chave: String): String; fun botoesVisiveis(botoes: List<String>): List<String>; fun corFundo(escuro: Boolean): Int; fun corPilula(escuro: Boolean): Int; fun corIcone(escuro: Boolean): Int }`
  - `interface AppAlvo { val pacote: String; val estilo: EstiloBarra; fun lerBarra(root: AccessibilityNodeInfo, modoEscuro: Boolean): LeituraBarra?; fun telaVideoCurto(root: AccessibilityNodeInfo): String? }`
  - `class InstagramAlvo(val assinaturas: Assinaturas, ctx: Context) : AppAlvo` com `companion { const val PACOTE = "com.instagram.android" }`

- [ ] **Step 1: Criar `AppAlvo.kt`**

```kotlin
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
```

- [ ] **Step 2: Criar `InstagramAlvo.kt`**

```kotlin
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
```

- [ ] **Step 3: Ligar o serviço ao `InstagramAlvo`**

Em `ReelsBlockerService.kt`:

(a) Troque o campo
```kotlin
    private lateinit var assinaturas: Assinaturas
```
por
```kotlin
    private lateinit var alvo: InstagramAlvo
```

(b) Em `onServiceConnected`, troque
```kotlin
        assinaturas = Assinaturas.carregar(this)
```
por
```kotlin
        alvo = InstagramAlvo(Assinaturas.carregar(this), this)
```

(c) Remova do `companion object` a linha `const val PKG_INSTAGRAM = "com.instagram.android"`.

(d) Em `onAccessibilityEvent`, troque `if (pacote != PKG_INSTAGRAM) {` por `if (pacote != alvo.pacote) {`.

(e) Em `verificarBarra`, troque
```kotlin
        if (root == null || root.packageName?.toString() != PKG_INSTAGRAM) return esconderOverlay()
```
por
```kotlin
        if (root == null || root.packageName?.toString() != alvo.pacote) return esconderOverlay()
```
troque
```kotlin
        val idReels = reelsAbertoId(root)
```
por
```kotlin
        val idReels = alvo.telaVideoCurto(root)
```
e troque
```kotlin
        val leitura = BarraReader.ler(root, assinaturas, modoEscuro())
```
por
```kotlin
        val leitura = alvo.lerBarra(root, modoEscuro())
```

(f) Apague a função inteira:
```kotlin
    private fun reelsAbertoId(root: AccessibilityNodeInfo): String? =
        assinaturas.reelsViewerIds.firstOrNull { id -> algumVisivel(root, id) }
```

(g) Em `reelsPermitido`, troque `assinaturas.reelsChatIds.any` por `alvo.assinaturas.reelsChatIds.any`.

(h) Em `vigia`, troque `Decisoes.saiuDoInstagram(ativo, PKG_INSTAGRAM)` por `Decisoes.saiuDoInstagram(ativo, alvo.pacote)`.

(i) Confirme que não sobrou nada:
```bash
grep -n "PKG_INSTAGRAM\|assinaturas\b" app/src/main/java/com/murilo/bloqueadorreels/ReelsBlockerService.kt
```
Expected: só ocorrências `alvo.assinaturas` (linha de `reelsChatIds`). Nenhum `PKG_INSTAGRAM`.

- [ ] **Step 4: Compilar e testar**

```bash
./gradlew.bat testDebugUnitTest assembleDebug --no-daemon
```
Expected: `BUILD SUCCESSFUL`, todos os testes verdes.

- [ ] **Step 5: Verificar no emulador (bloqueio e reserva)**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put secure enabled_accessibility_services com.murilo.bloqueadorreels/com.murilo.bloqueadorreels.ReelsBlockerService
```
No app: desligue "Reels por link" e "Modo de mapeamento".
```bash
adb logcat -c
adb shell am start -a android.intent.action.VIEW -d instagram://reels_home -p com.instagram.android
sleep 4
adb logcat -d -s ReelsBlocker
```
Expected: linha `tela de Reels (com.instagram.android:id/clips_viewer_view_pager): VOLTAR` e o Instagram cai no feed (comportamento atual).

Teste do reserva: no JSON de debug, troque temporariamente o `clips_viewer_view_pager` de `reelsViewerIds` por `com.instagram.android:id/nao_existe`, recompile, reinstale e repita o comando.
Expected: o log mostra `tela de Reels (<ID de reserva>): VOLTAR` e o Reels é bloqueado mesmo sem o ID principal. **Reverta a troca no JSON** (`git diff app/src/main/assets/assinaturas.json` deve mostrar só o `reelsReservaIds`).

Falso positivo: abra feed, Explorar, Perfil e Mensagens tocando na barra por 1 min e rode `adb logcat -d -s ReelsBlocker`.
Expected: nenhuma linha `tela de Reels`. Se aparecer, o ID de reserva envolvido não é exclusivo: volte à Task 1 e troque-o.

ANR: role o feed por 2 min e confira `adb logcat -d | grep -i "ANR in"`. Expected: vazio.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/murilo/bloqueadorreels/AppAlvo.kt app/src/main/java/com/murilo/bloqueadorreels/InstagramAlvo.kt app/src/main/java/com/murilo/bloqueadorreels/ReelsBlockerService.kt
git commit -m "AppAlvo/InstagramAlvo: pacote, leitura da barra e detecção de Reels com sinal reserva

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Extrair o desenho para `BarraOverlay`

**Files:**
- Create: `app/src/main/java/com/murilo/bloqueadorreels/BarraOverlay.kt`
- Modify: `app/src/main/java/com/murilo/bloqueadorreels/ReelsBlockerService.kt`

**Interfaces:**
- Consumes: `EstiloBarra`, `EstadoBarra`.
- Produces:
  - `class BarraOverlay(servico: AccessibilityService, estilo: EstiloBarra, aoTocar: (chave: String, longo: Boolean) -> Unit)`
  - `enum class BarraOverlay.Resultado { ATUALIZADO, CRIADO, FALHOU }`
  - `fun mostrar(estado: EstadoBarra): Resultado`, `fun remover()`, `fun destacar(chave: String): EstadoBarra?`, `val visivel: Boolean`, `val estadoDesenhado: EstadoBarra?`

Nota de correção embutida: no código antigo, `overlay = criarBarra(estado).also { ... onFailure { overlay = null } }` sobrescrevia o `null` com a view quando `addView` falhava (a atribuição acontece depois do `also`). Aqui a falha é tratada de verdade.

- [ ] **Step 1: Criar `BarraOverlay.kt`**

```kotlin
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
```

- [ ] **Step 2: Compilar só o arquivo novo**

```bash
./gradlew.bat assembleDebug --no-daemon
```
Expected: `BUILD SUCCESSFUL` (o serviço ainda tem a cópia antiga do desenho; nada conflita porque os nomes são privados).

- [ ] **Step 3: Trocar o desenho do serviço pela `BarraOverlay`**

Em `ReelsBlockerService.kt`:

(a) No companion, apague `private val COR_FUNDO_ESCURO = Color.parseColor("#0C1014")`.

(b) Troque os campos
```kotlin
    private var overlay: ViewGroup? = null
    private var estadoDesenhado: EstadoBarra? = null
```
por
```kotlin
    private var barra: BarraOverlay? = null // criada ao conectar; nula antes (e se o serviço nunca conectou)
```

(c) Em `onServiceConnected`, logo depois de `alvo = InstagramAlvo(...)`:
```kotlin
        barra = BarraOverlay(this, alvo.estilo) { chave, longo ->
            acionarBotaoReal(
                chave,
                if (longo) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
            )
        }
```

(d) Em `verificarBarra`, troque
```kotlin
        if (!estado.visivel) removerOverlay() else desenharBarra(estado)
```
por
```kotlin
        if (!estado.visivel) removerOverlay() else mostrarBarra(estado)
```

(e) Apague a seção inteira de desenho, ou seja, as funções (e comentários): `iconePara`, `dp`, `criarBadge`, `criarPilula`, `descricaoPara`, `ehVertical`, `criarBarra`, `botoesVisiveis`, `preencherEspaco`, `mesmaEstrutura`, `atualizarBarra`, `desenharBarra`. Mantenha `modoEscuro()`.

(f) No lugar de `desenharBarra`, acrescente:
```kotlin
    private fun mostrarBarra(estado: EstadoBarra) {
        // Só uma janela nova precisa (re)armar a vigia; atualização no lugar não.
        if (barra?.mostrar(estado) != BarraOverlay.Resultado.CRIADO) return
        ultimaConfirmacaoPacote = SystemClock.uptimeMillis()
        ultimaJanelaAtivaId = janelaAtivaId()
        garantirVigia()
    }
```

(g) Em `acionarBotaoReal`, troque
```kotlin
            val atual = estadoDesenhado
            if (overlay != null && atual != null) {
                val novo = atual.copy(selecionado = chave)
                atualizarBarra(novo)
                ultimoEstado = novo
            }
```
por
```kotlin
            barra?.destacar(chave)?.let { ultimoEstado = it }
```

(h) Em `vigia`, troque `if (overlay == null && (foraDoInstagram || !permitirLink)) return` por
```kotlin
            if (barra?.visivel != true && (foraDoInstagram || !permitirLink)) return
```

(i) Troque `removerOverlay` por
```kotlin
    /** Remove o overlay mantendo `ultimoEstado` (usado ao redesenhar). */
    private fun removerOverlay() {
        barra?.remover()
        ultimaJanelaAtivaId = -1
    }
```
(`esconderOverlay` continua igual.)

(j) Remova os imports que o serviço não usa mais: `android.content.res.ColorStateList`, `android.graphics.Color`, `android.graphics.PixelFormat`, `android.graphics.Typeface`, `android.graphics.drawable.GradientDrawable`, `android.util.TypedValue`, `android.view.Gravity`, `android.view.View`, `android.view.ViewGroup`, `android.view.ViewGroup.LayoutParams.MATCH_PARENT`, `android.view.WindowManager`, `android.widget.FrameLayout`, `android.widget.ImageButton`, `android.widget.ImageView`, `android.widget.LinearLayout`, `android.widget.TextView`. Mantenha `Rect`, `Build`, `Handler`, `Looper`, `SystemClock`, `Log`, `Toast`, `Configuration`, `Intent`, `SharedPreferences`, `AccessibilityEvent`, `AccessibilityNodeInfo`.

- [ ] **Step 4: Conferir o tamanho e referências soltas**

```bash
wc -l app/src/main/java/com/murilo/bloqueadorreels/ReelsBlockerService.kt
grep -n "overlay\b\|estadoDesenhado\|criarBarra\|desenharBarra\|PixelFormat\|GradientDrawable" app/src/main/java/com/murilo/bloqueadorreels/ReelsBlockerService.kt
```
Expected: ~330 linhas; o `grep` não devolve nada (comentários antigos que mencionem "overlay" por texto são aceitáveis, mas nenhum identificador).

- [ ] **Step 5: Compilar e testar**

```bash
./gradlew.bat testDebugUnitTest assembleDebug --no-daemon
```
Expected: `BUILD SUCCESSFUL`, testes verdes.

- [ ] **Step 6: Verificar no emulador (aparência e toques)**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put secure enabled_accessibility_services com.murilo.bloqueadorreels/com.murilo.bloqueadorreels.ReelsBlockerService
adb shell monkey -p com.instagram.android -c android.intent.category.LAUNCHER 1
sleep 3
MSYS_NO_PATHCONV=1 adb exec-out screencap -p > planejamento/medicoes/dump-barra-pos.png
```
Abra o PNG (Read): a barra substituta aparece sem o botão Reels, igual à de antes (compare com `playstore/screenshots/`). Depois:
- toque em Explorar, Mensagens e Perfil: o destaque (pílula + ícone preenchido) muda na hora e a tela troca, sem a barra real piscar;
- toque longo no Perfil: abre o seletor de contas;
- `adb shell cmd uimode night yes`, confira a barra no escuro (`#0C1014`), depois `adb shell cmd uimode night no`;
- `adb shell am start -a android.intent.action.VIEW -d instagram://reels_home -p com.instagram.android` com as opções de link/chat desligadas: bloqueado por `BACK` (`adb logcat -d -s ReelsBlocker`);
- ligue "Reels por link" e repita o comando com o Instagram fechado (`am force-stop`): o Reels abre e fica liberado; deslize para o próximo: volta a ser bloqueado.
Expected: sem diferença do comportamento anterior à refatoração.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/murilo/bloqueadorreels/BarraOverlay.kt app/src/main/java/com/murilo/bloqueadorreels/ReelsBlockerService.kt
git commit -m "BarraOverlay: desenho da barra substituta fora do serviço, genérico por EstiloBarra

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```
(O `dump-barra-pos.png` fica fora do commit: `git status` deve mostrá-lo como não rastreado; apague-o.)

---

### Task 6: Fechamento — verificação total e documentação

**Files:**
- Modify: `CLAUDE.md` (seção "Estrutura")
- Modify: `playstore/declaracao-acessibilidade.md` (linha 26)

- [ ] **Step 1: Verificação completa**

```bash
./gradlew.bat testDebugUnitTest bundleRelease lintRelease --no-daemon
grep -rn "PKG_INSTAGRAM" app/src
grep -rn "com.instagram.android\"" app/src/main/java
```
Expected: `BUILD SUCCESSFUL`, lint sem erro; o primeiro `grep` vazio; o segundo só em `InstagramAlvo.kt` (constante `PACOTE`).

- [ ] **Step 2: Atualizar a estrutura no `CLAUDE.md`**

Na seção "## Estrutura", troque as três primeiras linhas da lista (`ReelsBlockerService`, `BarraReader`, `Assinaturas`...) por:

```markdown
- `ReelsBlockerService` — ciclo de vida, eventos, debounce 30 ms, vigia, bloqueio de Reels (fase 4), link/chat, saúde, modo de mapeamento (fase 1). Usa `alvo: InstagramAlvo` e `barra: BarraOverlay`.
- `AppAlvo` / `InstagramAlvo` — contrato por plataforma (pacote, `lerBarra`, `telaVideoCurto`, `EstiloBarra`). Plataforma nova (YouTube, TikTok) = nova implementação. `telaVideoCurto` aceita o ID principal ou ≥ 2 `reelsReservaIds` juntos (`Decisoes.telaVideoCurto`).
- `BarraOverlay` — janela e views da barra substituta; genérica (ícones, cores e descrições vêm do `EstiloBarra`). `mostrar` atualiza no lugar se a estrutura não mudou (sem piscar).
- `BarraReader` — `lerEstado` (fase 2). Sem certeza => `null` => não desenha.
- `Assinaturas` + `assets/assinaturas.json` — IDs/descrições (pt/en) dos botões e IDs de reserva do Reels (`reelsReservaIds`, no máximo 4, medidos em `planejamento/medicoes/`). `filesDir/assinaturas.json` sobrescreve o asset (gancho do passo 7).
```
Mantenha a linha `Prefs`, `MainActivity` como está.

- [ ] **Step 3: Declaração de Acessibilidade**

Em `playstore/declaracao-acessibilidade.md`, na linha 26, troque
`e verifica se o visualizador de Reels está visível.`
por
`e verifica se o visualizador de Reels está visível (pelo ID principal do visualizador ou, se ele mudar, por alguns outros IDs da mesma tela; só a presença das views é conferida).`

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md playstore/declaracao-acessibilidade.md
git commit -m "Docs: estrutura com AppAlvo/BarraOverlay e declaração com os IDs de reserva

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
git status --short
```
Expected: árvore limpa (nenhum dump nem PNG de medição pendente).

---

## Self-review (feita ao escrever)

**Cobertura do spec:** `BarraOverlay` (Task 5) com API, regras de não-piscar e vertical; `AppAlvo` + `InstagramAlvo` + `EstiloBarra` (Task 4); sinal reserva puro em `Decisoes` com testes (Task 2), validado por `Assinaturas.valida()` (Task 3), IDs medidos no emulador (Task 1); serviço sem desenho e sem `PKG_INSTAGRAM` (Tasks 4 e 5, conferido na Task 6); testes antigos preservados e novos; verificação no emulador; `accessibility_config.xml` intocado. Desvio do spec, registrado na Task 1: a condição "`tab_bar` ausente" foi trocada por "≥ 2 IDs de reserva juntos".

**Placeholders:** os únicos valores não escritos aqui são os IDs reais de reserva e a versão do Instagram, que só existem depois da medição da Task 1 (o plano diz onde são produzidos e onde entram). Os testes não dependem de IDs literais.

**Consistência de tipos:** `telaVideoCurto(principalVisivel, reservaVisiveis)` (Task 2) é usada em `InstagramAlvo` (Task 4); `MIN_RESERVA` em `AssinaturasTest` (Task 3); `EstiloBarra` (Task 4) tem os mesmos seis métodos usados por `BarraOverlay` (Task 5); `BarraOverlay.Resultado`, `mostrar`, `destacar`, `remover`, `visivel` batem com o serviço.

**Risco conhecido:** `noPrimeiroReels` usa o nó do ID que casou; se for um ID de reserva, o nó pode não oferecer `ACTION_SCROLL_BACKWARD` e contar como "primeiro" mesmo após deslizar. Só afeta a liberação por link com o ID principal quebrado; a opção está desligada por padrão.
