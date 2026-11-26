# Bloqueador de Reels

App Android que esconde a aba Reels do Instagram e volta sozinho se o Reels abrir por vídeo, link ou mensagem. Usa um Serviço de Acessibilidade, só depois do consentimento do usuário. Tudo roda no aparelho: o app não tem permissão de internet.

- **Política de privacidade e termos:** https://murilsanches.github.io/bloqueador-reels/
- **Status:** v0.1.0, em fase de publicação na Google Play.
- App independente, sem afiliação com o Instagram ou a Meta.

## Desenvolvimento

Kotlin, Jetpack Compose, minSdk 26. Veja `CLAUDE.md` (build, testes, emulador) e `SEGURANCA.md` (revisão de segurança). O pacote de publicação está em `playstore/`.

```
./gradlew testDebugUnitTest assembleDebug
```

A chave de assinatura (`release.keystore`, `keystore.properties`) não faz parte do repositório.

## Contato

Murilo Studios · murilosanchesp@gmail.com
