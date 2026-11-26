# Segurança dos dados, classificação e demais formulários

## Segurança dos dados (Play Console › Conteúdo do app › Segurança dos dados)

| Pergunta | Resposta | Motivo |
|---|---|---|
| O app coleta ou compartilha dados do usuário? | **Não** | O app não tem a permissão de internet; o que lê da tela é processado só no aparelho e descartado. A definição da Play de "coletar" é enviar dados para fora do aparelho. |
| Dados criptografados em trânsito | Não se aplica | Nada é transmitido |
| Posso pedir exclusão dos dados? | Não se aplica | Nada é coletado. Preferências locais somem ao desinstalar |
| Política de privacidade | **https://murilsanches.github.io/bloqueador-reels/politica-de-privacidade.html** | Obrigatória por usar Acessibilidade. Também está dentro do app (rodapé), como a Play exige |

Conferência técnica (v0.1.0, `aapt2 dump permissions` no APK de release): nenhuma permissão de uso do Android (sem internet, localização, contatos, armazenamento etc.). Aparece só `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, permissão interna no namespace do próprio app que a biblioteca AndroidX adiciona, e o vínculo do serviço (`BIND_ACCESSIBILITY_SERVICE`) no `<service>`. Sem SDK de terceiros de análise, anúncios ou rede. `allowBackup=false`.

## Classificação de conteúdo (questionário IARC)

- Categoria: **Utilitário, produtividade, comunicação ou outros**.
- Todas as perguntas (violência, conteúdo sexual, linguagem, drogas, jogos de azar, interação entre usuários, compartilhamento de localização, compras digitais): **Não**.
- Resultado esperado: **Livre / PEGI 3**.

## Outros formulários de "Conteúdo do app"

| Formulário | Resposta |
|---|---|
| Anúncios | Não contém anúncios |
| Público-alvo | **13 anos ou mais** (não é voltado a crianças; o Instagram exige 13+) |
| App de notícias | Não |
| Apps governamentais | Não |
| Recursos financeiros | Nenhum |
| Aplicativo de saúde | Não |
| Permissão de ID de publicidade | Não usa |
| Acesso a todos os arquivos / SMS / localização / chamadas | Não usa |

## Países e preço

Gratuito. Distribuição: Brasil (e/ou todos). Dispositivos: apenas celulares (Android 8.0+, API 26+).
