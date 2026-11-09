# Gravalix

Gravador de tela para Android. **Sem marca d’água**, grava por cima de qualquer app e salva em `.mp4`.

## O que ele faz

- Grava a tela em **HD 720p (30 fps)**, **Full HD 1080p (30 fps)** ou **Full HD 1080p (60 fps)**
- Você escolhe o áudio de cada gravação:
  - **Som do telefone** (o áudio que toca no aparelho)
  - **Microfone**
  - **Som do telefone + microfone** (os dois juntos)
  - **Mudo**
- **Botão flutuante** por cima dos outros apps, com tempo, pausar/continuar e parar. Ele aparece para você durante a gravação e o Gravalix tenta escondê-lo do vídeo final
- Controle também pela **notificação** (pausar, continuar, parar)
- **Contagem de 3 segundos** antes de começar
- Modo **horizontal** para gravar jogos
- **Minhas gravações**: lista dos vídeos com miniatura, assistir dentro do app, compartilhar, enviar para o **YouTube** e excluir
- Os vídeos ficam na pasta **Filmes/Gravalix** (aparecem também na Galeria)

## Como baixar e instalar

Precisa de Android 10 ou mais novo (testado como alvo em Android 13).

1. No celular, abra: **https://github.com/playerzin13mv/gravalix/releases/latest**
2. Toque em **Gravalix.apk** para baixar.
3. Abra o arquivo baixado (pelo aviso do navegador, pelo app **Arquivos** ou pelo **ZArchiver**) e toque em **Instalar**.
4. Se o Android avisar que o navegador/gerenciador não pode instalar apps, toque em **Configurações** e ative **Permitir desta fonte**. Depois volte e toque em Instalar.
5. Se aparecer um aviso do **Play Protect**, toque em **Mais detalhes → Instalar mesmo assim**. O aviso aparece porque o app não vem da Play Store.

Para atualizar, é só baixar o APK novo e instalar por cima. Seus vídeos não são apagados.

## Como usar

1. Abra o **Gravalix**.
2. Escolha o **áudio**, a **qualidade** e se quer o **botão flutuante**.
3. Toque em **Iniciar gravação**.
4. Na primeira vez o Android pede algumas permissões:
   - **Exibir sobre outros apps** (para o botão flutuante): ative o Gravalix e volte ao app
   - **Microfone / áudio** (para qualquer opção com som)
   - **Notificações**
   - **Gravar a tela**: toque em **Iniciar agora**
5. O Gravalix volta para a tela inicial e conta 3, 2, 1. Abra o app que você quer gravar.
6. Para pausar ou parar, use o **botão flutuante** (dá para arrastar) ou a **notificação**.
7. Ao parar, o vídeo é salvo e aparece em **Minhas gravações**.

## Perguntas frequentes

**O botão flutuante apareceu no vídeo (ou ficou um quadrado escuro).**
Esconder o botão do vídeo depende do aparelho e não funciona igual em todos. Se acontecer no seu: desligue **Botão flutuante durante a gravação** e controle tudo pela notificação.

**O som do telefone saiu mudo.**
O Android bloqueia a captura do som interno em alguns apps (certos apps de streaming, ligações e apps que proíbem a captura). Nesse caso o app grava em silêncio. Use a opção **Microfone** ou **Som do telefone + microfone**. A captura do som interno só existe no Android 10 ou mais novo.

**O botão para permitir “Exibir sobre outros apps” está cinza.**
Vá em **Configurações → Apps → Gravalix**, toque nos três pontinhos no canto e escolha **Permitir configurações restritas**. Depois ative a permissão.

**O vídeo pausado ficou com um pequeno salto.**
Ao continuar, o Gravalix espera o próximo quadro completo do vídeo para não estragar a imagem. Pode levar até 2 segundos.

**Gravei na vertical e abri um jogo na horizontal.**
O tamanho do vídeo é decidido na hora de iniciar. Para jogos, ligue **Gravar na horizontal (jogos)** antes de iniciar.

**Para excluir um vídeo.**
Em **Minhas gravações**, toque em **Excluir**. O Android pode pedir uma confirmação.

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
| `app/src/main/java/com/gravalix/app/MainActivity.kt` | Tela principal: escolhas, permissões e início |
| `app/src/main/java/com/gravalix/app/RecorderService.kt` | Serviço da gravação: contagem, botão, notificação, pausar e parar |
| `app/src/main/java/com/gravalix/app/ScreenRecorder.kt` | Motor: captura a tela e o áudio e grava o `.mp4` |
| `app/src/main/java/com/gravalix/app/FloatingControls.kt` | Botão flutuante |
| `app/src/main/java/com/gravalix/app/RecordingsActivity.kt` | Lista de gravações, compartilhar e YouTube |
| `app/src/main/java/com/gravalix/app/PlayerActivity.kt` | Player de vídeo |
| `.github/workflows/build.yml` | Compilação automática no GitHub |
| `icone/` | Ícone original (1024×1024) e versão 512×512 |

A chave de assinatura (`app/gravalix.jks`) está no projeto de propósito, para que todo APK novo consiga atualizar o anterior. Ela serve para instalação por APK, não para publicar na Play Store.

## Privacidade

O Gravalix não usa internet e não envia nada para lugar nenhum. Os vídeos ficam só no seu telefone, até você compartilhar.
