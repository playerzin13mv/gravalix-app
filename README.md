# Gravalix

Gravador de tela para Android. **Sem marca d’água**, grava por cima de qualquer app e salva em `.mp4`.

## O que ele faz

- **Tela inicial com a lista dos seus vídeos**: miniatura, assistir dentro do app, compartilhar, enviar para o **YouTube** e excluir
- **Bolha flutuante** por cima dos apps **antes e depois** de gravar. Você começa a gravar de qualquer lugar, sem abrir o Gravalix
- A bolha **some durante a gravação**, então nunca aparece no vídeo. Ela volta quando você para
- A bolha **fica só nas laterais** da tela e tem uma **lixeira** para tirá-la sem abrir o app
- **Bolha personalizável**: **foto sua como ícone da bolha**, tamanho, opacidade, cor e quais botões aparecem no menu
- Se quiser a bolha **visível no vídeo** (com a sua foto, como um logo), é só trocar o modo em Configurações
- **Caneta**: desenhe ou circule coisas na tela enquanto grava. O desenho aparece no vídeo
- **Mostrar toques** do próprio app: um círculo no que você toca, visível no vídeo
- **Contagem configurável**: sem contagem, 3, 5 ou 10 segundos
- **Áudio** à sua escolha: som do telefone, microfone, os dois ou mudo
- **Qualidade**: HD 720p/30, Full HD 1080p/30 ou Full HD 1080p/60
- **Pausar e continuar**, modo **horizontal** para jogos
- **Live (transmissão ao vivo)** para Twitch, YouTube, Facebook ou qualquer servidor RTMP, com **cena editável**: **PNGTuber** que abre a boca quando você fala, imagens e textos por cima da tela
- **Tema** Claro, Escuro ou Sistema, e **animações de transição** (dá para desligar)
- **Relatório de erros** e atalho para liberar o app da economia de bateria, se algo parar sozinho
- **Extras**: botão no **painel rápido** (barra de cima) e **agitar o celular para parar**, os dois sem nada aparecendo na tela
- Os vídeos ficam em **Filmes/Gravalix** (aparecem também na Galeria)

## Como baixar e instalar

Precisa de Android 10 ou mais novo.

1. No celular, abra: **https://github.com/playerzin13mv/gravalix/releases/latest**
2. Toque em **Gravalix.apk** para baixar.
3. Abra o arquivo baixado (pelo aviso do navegador, pelo app **Arquivos** ou pelo **ZArchiver**) e toque em **Instalar**.
4. Se o Android avisar que o app não pode instalar de fontes desconhecidas, toque em **Configurações** e ative **Permitir desta fonte**. Depois volte e toque em Instalar.
5. Se aparecer um aviso do **Play Protect**, toque em **Mais detalhes → Instalar mesmo assim**. O aviso aparece porque o app não vem da Play Store.

Para atualizar, é só baixar o APK novo e instalar por cima. Seus vídeos não são apagados.

## Como usar

### Primeira vez
1. Abra o **Gravalix**. A tela inicial é a lista dos seus vídeos.
2. Ligue a chave **Bolha flutuante**. O Android pede “Exibir sobre outros apps”: ative o Gravalix e volte.
3. Toque em **Configurações** para escolher áudio, qualidade e contagem.

### Gravando
- **Pelo app:** toque em **Iniciar gravação**. O Gravalix volta para a tela inicial e conta (se a contagem estiver ligada).
- **Pela bolha:** em qualquer app, toque na bolha e depois no botão vermelho. Você não precisa abrir o Gravalix.
- Na primeira gravação o Android pede **microfone**, **notificações** e **gravar a tela** (toque em **Iniciar agora**).

### A bolha
- Ela fica **encaixada na lateral** da tela. Arraste para cima ou para baixo, ou para o outro lado. Ao soltar, ela gruda na lateral mais perto.
- Toque nela para abrir o menu (ele abre para o lado de dentro da tela).
- **Lixeira:** ao começar a arrastar a bolha, aparece uma lixeira embaixo. Solte a bolha em cima dela para tirá-la da tela, sem abrir o app. Para ela voltar, ligue **Bolha flutuante** no Gravalix.
- **Durante a gravação a bolha some.** Pare pela notificação, pelo painel rápido ou agitando o celular. Quando a gravação termina, ela volta.
- Em **Configurações → Personalizar a bolha** você escolhe uma **foto para o ícone da bolha** (**Escolher foto**; **Usar o ícone padrão** volta ao original) e muda tamanho, opacidade, cor e quais botões aparecem.
- Quer que a bolha **apareça na gravação**, por exemplo com a sua foto? Em **Configurações → Bolha durante a gravação**, escolha **Fica visível no vídeo**. Nesse modo o menu também aparece no vídeo quando você o abre.

| Botão | O que faz |
| --- | --- |
| Vermelho / quadrado | Começa / para a gravação |
| Pausa / play | Pausa e continua (só aparece gravando) |
| Lápis | Liga a caneta |
| Círculo com anel | Liga / desliga **mostrar toques** |
| Casa | Abre o Gravalix |
| X | Fecha a bolha |

### A caneta
Ao tocar no lápis, a tela inteira vira um quadro transparente e aparece uma barrinha no topo:

| Botão | O que faz |
| --- | --- |
| Lápis colorido | Troca a cor (vermelho, amarelo, verde, azul, branco) |
| Seta | Desfaz o último traço |
| Lixeira | Apaga tudo |
| Círculo claro | Traços somem sozinhos depois de uns 4 segundos |
| Mão | Modo “mão”: você mexe nos apps normalmente e o desenho continua na tela |
| X | Fecha a caneta |

A barrinha e a bolha ficam fora do vídeo. **O desenho aparece no vídeo**, de propósito.

### Parando
Use a bolha, a **notificação**, o botão do **painel rápido** ou **agite o celular 3 vezes** (ative em Configurações). Ao parar, o vídeo aparece na lista.

### Botão no painel rápido
Puxe a barra de cima, toque no lápis de editar e arraste o **Gravalix** para os botões ativos. Daí é só tocar nele para gravar e parar.

## Live e PNGTuber

1. Na tela inicial toque em **Live**.
2. Escolha a plataforma (Twitch, YouTube, Facebook ou Outro), confira o endereço do servidor e **cole a chave da transmissão** (fica no painel do criador da plataforma).
3. Toque em **Editar a cena**:
   - **+ PNGTuber**: escolha uma imagem de boca fechada. Depois, em **Imagem falando**, escolha a de boca aberta. Ele abre a boca quando o microfone te ouve.
   - **+ Imagem** e **+ Texto** para logos, molduras, avisos etc.
   - Arraste os itens na prévia (que tem o formato da sua tela) e use **dois dedos** para mudar o tamanho. Dá para espelhar, trazer para a frente e apagar.
   - **Testar a fala** mostra a boca aberta na prévia.
4. Volte e toque em **Iniciar a Live**. Os itens da cena aparecem por cima de tudo, na transmissão e no vídeo salvo.
5. Para encerrar, use **Parar** na notificação, o painel rápido ou agite o celular.

Notas: a live usa o microfone (a voz e o PNGTuber precisam dele). Tudo que aparece na tela vai para a live, inclusive notificações; ligue o **Não perturbe**. Uma cópia da live fica salva em Minhas gravações. Se a internet cair, a gravação no celular continua. O recurso de Live é novo: teste com uma transmissão privada/não listada antes de usar de verdade. Para a cena aparecer também em gravações normais, ligue a opção no editor.

## Mostrar toques

O Android não deixa um app comum ver onde o dedo toca dentro de outros apps. Por isso o Gravalix tem dois jeitos, e usa o que estiver disponível:

**1) Toques do Gravalix (o padrão, sem comandos).**
1. Em **Configurações → Mostrar toques**, toque em **Abrir Acessibilidade**.
2. Procure **Gravalix – mostrar toques** (pode estar em “Apps instalados”) e ligue.
3. Se o Android disser “configuração restrita”, vá em **Configurações → Apps → Gravalix**, toque nos três pontinhos e escolha **Permitir configurações restritas**. Depois ligue o serviço.

Quando você grava com **Mostrar toques** ligado, o Gravalix desenha um círculo no **centro do botão ou item que você tocou**. Não é o ponto exato do dedo, e não aparece em jogos ou telas desenhadas sem botões. O serviço só desenha o círculo: ele não lê, guarda nem envia nada do que está na tela.

**2) Toques exatos do sistema (opcional, uma vez só pelo Termux).** Mostra o círculo no ponto exato do dedo, inclusive ao arrastar.
1. Instale o Gravalix e conecte o celular ao Wi-Fi.
2. Ligue **Opções do desenvolvedor → Depuração sem fio**.
3. No Termux, na pasta do projeto: `bash ativar-toques.sh`
4. O script pede o endereço e o código do pareamento. Use a **tela dividida** (Termux de um lado, Configurações do outro), porque a janela do código some se você sair dela.

Com essa permissão liberada, o Gravalix passa a usar os toques do sistema no lugar do círculo no item. Se você desinstalar o Gravalix, precisa refazer.

## Perguntas frequentes

**A bolha apareceu no vídeo (ou ficou um quadrado preto).**
Em **Configurações → Bolha durante a gravação**, deixe em **Some durante a gravação** (é o padrão). O outro modo é experimental: ele usa a proteção do Android e, em muitos celulares, deixa um quadrado preto no vídeo.

**Como paro a gravação se a bolha some?**
Pela notificação (botão **Parar**), pelo botão do painel rápido ou agitando o celular 3 vezes (ative em Configurações).

**O som do telefone saiu mudo.**
O Android bloqueia a captura do som interno em alguns apps (certos apps de streaming, ligações e apps que proíbem a captura). Use a opção **Microfone** ou **Som do telefone + microfone**.

**Não consigo ativar “Exibir sobre outros apps” (botão cinza).**
Vá em **Configurações → Apps → Gravalix**, toque nos três pontinhos e escolha **Permitir configurações restritas**. Depois ative a permissão.

**Gravei na vertical e abri um jogo na horizontal.**
O tamanho do vídeo é decidido na hora de iniciar. Para jogos, ligue **Gravar na horizontal** em Configurações.

**A bolha some e a gravação para do nada.**
Alguns celulares encerram apps em segundo plano. Em **Configurações → Se algo parar sozinho**, toque em **Liberar da economia de bateria** e deixe o Gravalix sem restrição. Não use apps de “limpar memória” (CCleaner e parecidos) durante a gravação. Se continuar, toque em **Enviar relatório de erros** e mande o texto.

**A bolha sumiu.**
Abra o Gravalix e confira se a chave **Bolha flutuante** está ligada. Ela desliga se você tocar no X do menu ou soltar a bolha na lixeira.

**O desenho da caneta não aparece no vídeo.**
Ele aparece sim. Se você usou o modo **Some totalmente** e a bolha não está na tela, o desenho continua gravando normalmente.

## Para quem quer compilar o app (Termux + GitHub)

O app é compilado pelo próprio GitHub, então não precisa de computador nem de Android Studio.

1. No site do GitHub, crie um repositório **vazio** chamado `Gravalix` (sem README e sem .gitignore).
2. Baixe o projeto (`Gravalix.zip`). No Termux:

   ```bash
   termux-setup-storage
   pkg install -y unzip git
   cd ~
   unzip ~/storage/downloads/Gravalix.zip -d ~/
   cd ~/Gravalix
   bash enviar-para-github.sh
   ```

3. O script pergunta seu usuário do GitHub e o nome do repositório, e envia o projeto usando o git que já está vinculado à sua conta (não pede login). Acompanhe em **Actions** (leva uns 5 minutos).
4. Quando terminar, o `Gravalix.apk` aparece em **Releases**.

Toda vez que você mudar o código e rodar `bash enviar-para-github.sh` de novo, o GitHub compila uma versão nova.

Se a compilação falhar, abra a execução em **Actions**, copie o erro (linhas com `error:` ou `e:`) e use para corrigir.

## Como o projeto é organizado

| Arquivo | Para que serve |
| --- | --- |
| `MainActivity.kt` | Tela inicial: lista de vídeos, gravar e chave da bolha |
| `SettingsActivity.kt` | Configurações |
| `BubbleService.kt` | Bolha flutuante, menu e barra da caneta |
| `PenCanvas.kt` | Quadro onde a caneta desenha |
| `StartActivity.kt` | Pede permissões e inicia a gravação (usada pela bolha, pelo app e pelo painel rápido) |
| `RecorderService.kt` | Serviço da gravação: contagem, notificação, pausar, parar |
| `ScreenRecorder.kt` | Motor: captura a tela e o áudio e grava o `.mp4` |
| `RecordingTileService.kt` | Botão do painel rápido |
| `TouchIndicator.kt` | Escolhe como mostrar os toques |
| `TouchAccessibilityService.kt` | Desenha o círculo no item tocado |
| `ShakeDetector.kt` | Agitar para parar |
| `LiveActivity.kt`, `LiveEditorActivity.kt` | Tela da Live e editor da cena |
| `LiveScene.kt`, `SceneView.kt`, `LiveOverlay.kt`, `AudioMeter.kt`, `AudioLevel.kt` | Cena (PNGTuber, imagens, textos), camada por cima da tela e volume do microfone |
| `RtmpClient.kt`, `RtmpSink.kt`, `LiveSink.kt` | Envio ao vivo por RTMP |
| `AppTheme.kt`, `BaseActivity.kt`, `AppLog.kt`, `GravalixApp.kt`, `Ui.kt` | Tema, animações, relatório de erros e peças de tela |
| `Prefs.kt`, `IconButton.kt`, `PlayerActivity.kt` | Configurações salvas, ícones dos botões e player |
| `.github/workflows/build.yml` | Compilação automática no GitHub |
| `enviar-para-github.sh`, `ativar-toques.sh` | Scripts do Termux |
| `icone/` | Ícone original (1024×1024) e versão 512×512 |

Todos os arquivos `.kt` ficam em `app/src/main/java/com/gravalix/app/`.

A chave de assinatura (`app/gravalix.jks`) está no projeto de propósito, para que todo APK novo consiga atualizar o anterior. Ela serve para instalação por APK, não para publicar na Play Store.

## Privacidade

O Gravalix só usa a internet quando você inicia uma **Live**, e envia o vídeo apenas para o servidor que você informou. Fora isso, não envia nada para lugar nenhum. Os vídeos, a cena e o relatório de erros ficam só no seu telefone, até você compartilhar.
