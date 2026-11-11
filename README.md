# Gravalix

Gravador de tela para Android. **Sem marca d’água**, grava por cima de qualquer app e salva em `.mp4`.

## O que ele faz

- **Tela inicial com a lista dos seus vídeos**: miniatura, assistir dentro do app, compartilhar, enviar para o **YouTube** e excluir
- **Bolha flutuante** por cima dos apps, **antes e durante** a gravação. Você começa a gravar de qualquer lugar, sem abrir o Gravalix
- A bolha **fica fora do vídeo** (veja as perguntas frequentes se aparecer um quadrado preto)
- **Caneta**: desenhe ou circule coisas na tela enquanto grava. O desenho aparece no vídeo
- **Mostrar toques**: um círculo onde o dedo toca, visível no vídeo
- **Contagem configurável**: sem contagem, 3, 5 ou 10 segundos
- **Áudio** à sua escolha: som do telefone, microfone, os dois ou mudo
- **Qualidade**: HD 720p/30, Full HD 1080p/30 ou Full HD 1080p/60
- **Pausar e continuar**, modo **horizontal** para jogos
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
Toque nela para abrir o menu. Dá para arrastá-la para onde quiser.

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

## Mostrar toques (uma configuração só)

O Android não deixa um app comum ligar os “toques na tela” sozinho. Há dois caminhos:

**Jeito fácil (manual):** ligue **Mostrar toques** em Configurações → Opções do desenvolvedor. O Gravalix abre essa tela para você.

**Jeito automático (uma vez só, pelo Termux):** assim o Gravalix liga e desliga os toques sozinho, pela bolha ou pelas Configurações.

1. Instale o Gravalix e conecte o celular ao Wi-Fi.
2. Ligue **Opções do desenvolvedor → Depuração sem fio**.
3. No Termux, na pasta do projeto:
   ```bash
   bash ativar-toques.sh
   ```
4. O script pede o endereço e o código do pareamento. Use a **tela dividida** (Termux de um lado, Configurações do outro), porque a janela do código some se você sair dela.

Isso só precisa ser feito uma vez. Se você desinstalar o Gravalix, precisa refazer.

## Perguntas frequentes

**A bolha apareceu no vídeo, ou ficou um quadrado preto no lugar.**
A bolha usa a proteção do Android para sair do vídeo, e isso se comporta diferente em cada fabricante. Em **Configurações → Bolha durante a gravação**, escolha **Some totalmente**. A bolha desaparece quando a gravação começa, e você controla pela notificação, pelo painel rápido ou agitando o celular.

**O som do telefone saiu mudo.**
O Android bloqueia a captura do som interno em alguns apps (certos apps de streaming, ligações e apps que proíbem a captura). Use a opção **Microfone** ou **Som do telefone + microfone**.

**Não consigo ativar “Exibir sobre outros apps” (botão cinza).**
Vá em **Configurações → Apps → Gravalix**, toque nos três pontinhos e escolha **Permitir configurações restritas**. Depois ative a permissão.

**Gravei na vertical e abri um jogo na horizontal.**
O tamanho do vídeo é decidido na hora de iniciar. Para jogos, ligue **Gravar na horizontal** em Configurações.

**A bolha sumiu.**
Abra o Gravalix e confira se a chave **Bolha flutuante** está ligada. Ela some se você tocar no X do menu.

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
| `TouchIndicator.kt` | Liga e desliga “mostrar toques” |
| `ShakeDetector.kt` | Agitar para parar |
| `Prefs.kt`, `IconButton.kt`, `PlayerActivity.kt` | Configurações salvas, ícones dos botões e player |
| `.github/workflows/build.yml` | Compilação automática no GitHub |
| `enviar-para-github.sh`, `ativar-toques.sh` | Scripts do Termux |
| `icone/` | Ícone original (1024×1024) e versão 512×512 |

Todos os arquivos `.kt` ficam em `app/src/main/java/com/gravalix/app/`.

A chave de assinatura (`app/gravalix.jks`) está no projeto de propósito, para que todo APK novo consiga atualizar o anterior. Ela serve para instalação por APK, não para publicar na Play Store.

## Privacidade

O Gravalix não usa internet e não envia nada para lugar nenhum. Os vídeos ficam só no seu telefone, até você compartilhar.
