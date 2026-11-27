# Revisão de segurança — Bloqueador de Reels v0.1.0

Data: 2026-11-26 · Escopo: código (Kotlin), manifesto, pacotes de release (APK/AAB) e comportamento em emulador (Android 17). Não coberto: aparelho real, ataques que exigem root/outro app malicioso com Acessibilidade, auditoria de CVEs das dependências.

## Superfície de ataque

| Item | Situação |
|---|---|
| Rede | Nenhuma. Sem `INTERNET`, sem WebView, sem sockets, sem URL no código |
| Permissões | Nenhuma de uso do Android. Só a permissão interna do AndroidX e o vínculo do serviço |
| Componentes exportados | Só `MainActivity` (launcher). Serviço `exported=false` + `BIND_ACCESSIBILITY_SERVICE`. Receiver do `profileinstaller` removido; provider do `androidx.startup` não exportado |
| Entrada externa | Nenhuma: `MainActivity` não lê extras nem URI; a única leitura de arquivo é `filesDir/assinaturas.json` (pasta privada) |
| Execução dinâmica | Nenhuma (`exec`, reflexão, `loadLibrary`, classes carregadas) |
| Backup | `allowBackup=false` + `dataExtractionRules` excluindo tudo (nuvem e transferência) |
| Depurável | Não (release: `flags=HAS_CODE ALLOW_CLEAR_USER_DATA`; `run-as` negado) |
| Logs | Release: 10 linhas por sessão, sendo 1 própria (`tela de Reels (<id>): VOLTAR`), sem conteúdo do Instagram. O mapeamento (despeja a árvore) só existe em build debug |

## Achados e correções

| # | Severidade | Achado | Correção |
|---|---|---|---|
| 1 | Média (privacidade) | O serviço recebia eventos de **todos** os apps, contra o que o aviso prometia | `android:packageNames="com.instagram.android"`: o sistema só entrega eventos do Instagram (testado: launcher, Chrome e sistema não geram evento). Como deixou de haver aviso de "saí do Instagram", entrou uma vigilância **só com a barra na tela**: a cada 80 ms confere (`flagRetrieveInteractiveWindows`, só o id da janela ativa) se ela mudou; se mudou, ou a cada 0,6 s, consulta apenas o nome do pacote da janela ativa (janela de outro app sem foco, como flutuante/PiP, só sai na checagem de 0,6 s) e some com a barra se não for o Instagram |
| 2 | Média | Arquivo de assinaturas sem validação (hoje local; amanhã remoto no passo 7) | `Assinaturas.valida()`: ≤ 64 KB, limites de listas e strings, **todo ID deve começar com `com.instagram.android:id/`**, exige botão `reels`. Inválido → cai no arquivo embutido |
| 3 | Baixa | Receiver exportado do `profileinstaller` (protegido por `DUMP`) sem uso | Removido no manifesto |
| 4 | Baixa | Sem regras de extração de dados (Android 12+) | `data_extraction_rules.xml` |
| 5 | Baixa | Erro de lint real: `stateDescription` só no Android 11+ (minSdk 26) | Protegido por `SDK_INT >= 30` |
| 6 | Info | Serviço lê conteúdo de qualquer app **tecnicamente** (o Android só filtra eventos, não leitura) | Não tem correção possível no SO. Mitigado no código: o pacote da janela é checado antes de qualquer leitura. Registrado com honestidade na declaração e na política |
| 7 | Info | `INTERNET` ausente impede, de propósito, o passo 7 (JSON remoto) | Se for implementado: HTTPS, assinatura do JSON verificada no app e revisão da política/aviso |

## Testes executados

| Teste | Resultado |
|---|---|
| Overlay não sobra fora do Instagram (HOME, Chrome por cima, tela apagar/acender, voltar ao app) | ✔ |
| Serviço sem consentimento fica inerte | ✔ |
| Arquivo adulterado: IDs de outro app / JSON quebrado / 70 KB / vazio (build debug) | ✔ todos ignorados, 0 crash, bloqueio segue funcionando |
| Iniciar serviço pelo shell (outro uid) | ✔ `Requires permission not exported` |
| `MainActivity` com extras e URI maliciosos | ✔ ignorados, 0 crash |
| `run-as` no release | ✔ `package not debuggable` |
| Segredos nos pacotes: senha da chave, `.keystore`, `.jks`, tokens, URLs | ✔ nenhum (só links de mensagens de bibliotecas e constantes do Compose) |
| Monkey no app: 3.000 eventos | ✔ 0 crash/ANR |
| Enxurrada: 120 rolagens no Instagram | ✔ 0 crash/ANR, 0 bloqueio falso, CPU 0%, memória 49→52 MB (sem sinal claro de vazamento) |
| Testes unitários (32) e lint | ✔ 0 falhas · 0 erros |
| Pacotes assinados | ✔ APK v2 verificado; AAB `jarsigner` ok |

## Riscos que continuam

- Um app **malicioso com Acessibilidade própria** pode clicar nos botões da nossa barra ou ler o que ela mostra (vale para qualquer app com esse recurso).
- O serviço não restringe, no nível do SO, a leitura de conteúdo ao Instagram. É uma garantia do código, não do Android.
- Sem auditoria de CVE das dependências (Compose, AndroidX). Não há rede nem entrada externa que as exponha.
- O teste de bloqueio de tela real não foi possível (emulador sem PIN): só a tela apagar/acender.
- Chave de assinatura (`release.keystore`): quem a tiver pode publicar atualizações como você. Guarde fora do projeto e não envie ao git.
