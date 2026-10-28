# Reel Séries Android V17

Projeto Android nativo (Activity Java + WebView com WebViewAssetLoader) que incorpora o catálogo original e preserva a interface. Compatível com GitHub e Android Studio.

## Como gerar APK
1. Abra esta pasta no Android Studio (JDK 17).
2. Aguarde a sincronização Gradle com internet.
3. Selecione **Build > Build APK(s)**. O APK estará em `app/build/outputs/apk/debug/`.
4. Para publicação, configure assinatura e política de conteúdo.

## Melhorias
- Requisições Firebase com timeout de 12 segundos.
- Imagens do catálogo com carregamento preguiçoso e fallback seguro.
- Busca limitada e encerrada após 80 resultados.
- WebView com aceleração por hardware, reprodução em tela cheia e navegação de retorno.
- Catálogo JSON embarcado e fonte Firebase existente preservada.

## Limites e testes
- Trata-se de um **aplicativo Android com interface web embutida**, não uma reescrita integral da interface em componentes nativos.
- Não foi possível compilar APK nem executar testes em aparelho neste ambiente; faça build e teste no Android Studio.
- O desempenho ainda pode ser afetado pelo processamento do catálogo grande, imagens remotas, qualidade dos streams e compatibilidade de codecs. HLS externo depende de conectividade com CDN.
- Confira permissões de distribuição do conteúdo e regras de segurança Firebase antes de publicar.


## Gerar APK pelo GitHub (sem Android Studio)
Envie **o conteúdo desta pasta** para a raiz de um repositório GitHub. Na aba **Actions**, abra **Gerar APK Android** e clique em **Run workflow**. Quando a execução terminar com sucesso, abra a execução e baixe o artefato **ReelSeries-Android-APK**. Extraia o ZIP do artefato para obter `app-debug.apk`, instalável no Android. O APK debug é assinado automaticamente para testes; não é uma versão de publicação na Play Store.
