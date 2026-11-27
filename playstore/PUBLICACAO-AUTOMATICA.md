# Publicação automática: `git push` na `main` = nova versão

Workflow: `.github/workflows/release.yml`.

## O que acontece a cada push

1. Roda os testes unitários e o lint. **Se falhar, nada é publicado.**
2. Gera o AAB assinado (chave vinda dos Secrets, apagada do runner no fim).
3. Cria um **GitHub Release** `v0.1.N` com o AAB anexado.
4. Se o Secret `PLAY_SERVICE_ACCOUNT_JSON` existir, **envia para a Google Play**, na faixa definida por `PLAY_TRACK` (padrão `internal`).

Pushes que só mexem em `docs/`, `playstore/`, `README.md`, `CLAUDE.md`, `SEGURANCA.md` ou `LICENSE` **não** geram versão. Para pular uma versão de propósito, ponha `[skip ci]` na mensagem do commit.

## Notas da versão

Geradas sozinhas: a lista de títulos dos commits desde a versão anterior (sem os que começam com `CI:` ou `docs:`) vira a seção "O que mudou" do GitHub Release (seguida do "Full Changelog" gerado pelo GitHub, com o link de comparação) e o "O que há de novo" da Play (cortado em 480 caracteres). **Escreva o título do commit pensando no usuário** (ex.: "Barra do Instagram não pisca mais ao trocar de aba"). Sem nenhum commit elegível, sai "Melhorias e correções."

## Versionamento

- `versionName` = `0.1.<nº da execução>` (base `VERSAO_BASE` no workflow; mude para `0.2` quando quiser subir minor/major).
- `versionCode` = `10 + nº da execução` (sempre crescente, como a Play exige; o primeiro envio manual usa `1`).
- Local, sem parâmetros: `1` / `0.1.0`.

## Secrets e variáveis do repositório

| Nome | Tipo | Para quê | Estado |
|---|---|---|---|
| `KEYSTORE_BASE64` | Secret | `release.keystore` em base64 | configurado |
| `KEYSTORE_PASSWORD` | Secret | senha do keystore | configurado |
| `KEY_ALIAS` | Secret | alias da chave | configurado |
| `KEY_PASSWORD` | Secret | senha da chave | configurado |
| `PLAY_SERVICE_ACCOUNT_JSON` | Secret | credencial da API da Play | **você configura depois da conta da Play** |
| `PLAY_TRACK` | Variável | `internal`, `alpha` (teste fechado), `beta` ou `production` | opcional (padrão `internal`) |
| `PLAY_STATUS` | Variável | `completed` ou `draft` | opcional (padrão `completed`) |

Mudar a faixa sem mexer no código: `gh variable set PLAY_TRACK --body alpha`.

## Configurar o envio para a Google Play (uma vez, depois que a conta estiver pronta)

1. **Primeiro envio é manual.** A API da Play não cria o app. Crie o app na Console e suba à mão o primeiro AAB (`playstore/bloqueador-reels-v0.1.0.aab`) em uma faixa (por exemplo teste interno), preenchendo os formulários.
2. **Conta de serviço:** Google Cloud Console → crie um projeto → *IAM e administrador › Contas de serviço* → criar conta → *Chaves › Adicionar chave › JSON* (baixa o arquivo).
3. **Ligar à Play:** Play Console → *Usuários e permissões › Convidar novos usuários* → e-mail da conta de serviço → permissões do app: *Lançar em faixas de teste* e *Lançar em produção* (conforme o uso).
4. **Ativar a API:** no mesmo projeto do Google Cloud, ative a *Google Play Android Developer API*.
5. **Guardar o JSON no GitHub:** `gh secret set PLAY_SERVICE_ACCOUNT_JSON < caminho\para\chave.json`. Apague o arquivo local depois.
6. A partir daí, cada push envia para `PLAY_TRACK`. Enquanto o app ainda não foi publicado pela primeira vez, a Play só aceita `PLAY_STATUS=draft`.

## Do teste à produção

- Durante os 14 dias de teste fechado: `gh variable set PLAY_TRACK --body alpha`.
- Quando a Play liberar produção: `gh variable set PLAY_TRACK --body production`. A partir daí **todo push na main vai para todos os usuários**: use `PLAY_STATUS=draft` (fica pronto para você publicar com um clique na Console) se quiser uma revisão final.
- Promover uma versão de uma faixa para outra também pode ser feito na Console, sem novo build.

## Segurança

- A chave de assinatura fica nos Secrets do GitHub (criptografados, nunca aparecem nos logs). Com o **Play App Signing**, ela é só a *chave de upload*: se vazar, a Play permite trocá-la.
- Só quem tem permissão de escrita no repositório dispara o release. Mantenha a conta do GitHub com autenticação em dois fatores.
- Releases do GitHub são públicos (o AAB assinado com a chave de upload não permite publicar nada na sua conta sem as credenciais da Play).
- Backup local da chave continua necessário (`release.keystore` e `keystore.properties`, fora do git).

## Rollback

A Play não permite reenviar um `versionCode` menor. Para desfazer uma versão ruim: faça o ajuste e dê outro push (nova versão), ou, na Console, interrompa a liberação e promova a versão anterior.
