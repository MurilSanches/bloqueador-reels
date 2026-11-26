# Bloqueador de Reels

App Android que esconde a aba Reels do Instagram e volta sozinho se o Reels abrir por vídeo, link ou mensagem. Usa um Serviço de Acessibilidade, só depois do consentimento do usuário. Tudo roda no aparelho: o app não tem permissão de internet.

- **Política de privacidade e termos:** https://murilsanches.github.io/bloqueador-reels/
- **Status:** em fase de publicação na Google Play. Cada push na `main` gera uma versão (ver `playstore/PUBLICACAO-AUTOMATICA.md`).
- **Licença:** [MIT](LICENSE) para o código. O nome, o ícone e a identidade visual "Bloqueador de Reels" / "Murilo Studios" não estão incluídos na licença (veja os Termos de uso).
- App independente, sem afiliação com o Instagram ou a Meta.

## Desenvolvimento

Kotlin, Jetpack Compose, minSdk 26. Veja `CLAUDE.md` (build, testes, emulador) e `SEGURANCA.md` (revisão de segurança). O pacote de publicação está em `playstore/`.

```
./gradlew testDebugUnitTest assembleDebug
```

A chave de assinatura (`release.keystore`, `keystore.properties`) não faz parte do repositório.

## Contato

Murilo Studios · murilosanchesp@gmail.com
