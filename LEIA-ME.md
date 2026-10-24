# Meu Launcher para TCL 50P7K (Google TV)

Launcher próprio com o visual da sua imagem: menu lateral, banner, linha de apps (Netflix, YouTube, Prime Video, Disney+, Max, Spotify, Todos), "Em destaque" e relógio.

## O que ele faz

- **Início:** banner, atalhos dos 6 apps favoritos e uma linha "Em destaque" com os outros apps instalados.
- **Apps:** grade com todos os apps da TV. Segure o botão OK sobre um app para fixá-lo em "Meus conteúdos".
- **Filmes e Séries:** mostra só os apps de streaming instalados.
- **TV ao vivo:** tenta abrir o app de TV ao vivo. No Google TV a aba "ao vivo" costuma ficar dentro da tela inicial original, então pode aparecer "Não foi possível abrir".
- **Configurações:** abre as configurações da TV.
- **Se um app favorito não estiver instalado**, o cartão fica apagado e, ao clicar, abre a página dele na Play Store.
- O botão Voltar leva sempre ao Início.

## 1. Colocar o seu papel de parede (opcional)

Copie sua imagem de fundo (sem a interface por cima) para:

    app/src/main/res/drawable-nodpi/wallpaper.jpg

Use 1920x1080, nome exatamente `wallpaper.jpg` (ou `wallpaper.png`). Sem esse arquivo, o launcher usa um degradê roxo.

## 2. Compilar o APK (no computador)

1. Instale o **Android Studio** (developer.android.com/studio).
2. Abra a pasta `TvLauncher` (File → Open). Aguarde o Gradle sincronizar; ele baixa tudo sozinho.
3. Menu **Build → Build APK(s)**.
4. Quando terminar, clique em "locate". O arquivo é `app/build/outputs/apk/debug/app-debug.apk`.

Se aparecer erro de versão do JDK, em Settings → Build Tools → Gradle escolha "Gradle JDK: 17" (o que vem com o Android Studio).

## 3. Ativar o ADB na TV

1. Configurações → Sistema → Sobre → clique 7 vezes em **Build do Android TV** (ou "Versão do SO").
2. Volte a Sistema → **Opções do desenvolvedor** e ative **Depuração USB** (e **Depuração de rede/ADB**, se aparecer).
3. Veja o IP da TV em Configurações → Rede e Internet → sua rede Wi-Fi.

## 4. Instalar pelo ADB (no computador, na mesma rede Wi-Fi)

Instale o **platform-tools** (developer.android.com/tools/releases/platform-tools) e rode no terminal:

    adb connect IP_DA_TV:5555

Aceite o pedido de autorização que aparece na TV ("Sempre permitir"). Depois:

    adb install -r caminho/para/app-debug.apk

## 5. Definir como tela inicial

    adb shell cmd package set-home-activity com.meu.tvlauncher/.MainActivity

Aperte o botão Home do controle. Se abrir o seu launcher, está pronto.

### Se a TV continuar abrindo a tela inicial original

Alguns Google TV ignoram o comando acima. Só então, e **depois de confirmar que seu launcher abre sozinho pelo ícone "Meu Launcher"**, desative a tela inicial original:

    adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx

Se esse pacote não existir na sua TV, tente `com.google.android.tvlauncher`. Veja quais existem com:

    adb shell pm list packages | grep -i launcher

## 6. Voltar ao original (se algo der errado)

    adb shell pm enable com.google.android.apps.tv.launcherx
    adb uninstall com.meu.tvlauncher

Se você usou o outro pacote, troque o nome no primeiro comando. Se ficar sem tela inicial, reinicie a TV e conecte o ADB de novo para rodar os comandos acima. Em último caso, um reset de fábrica restaura tudo.

## Observações

- O APK de debug é assinado automaticamente pelo Android Studio, então pode ser instalado direto.
- Não consegui compilar nem testar este projeto aqui. Se o Android Studio mostrar algum erro de compilação, me mande a mensagem completa que eu corrijo.
- O launcher não altera nenhum app nem coleta dados; a lista de apps e os itens fixados ficam só na TV.
