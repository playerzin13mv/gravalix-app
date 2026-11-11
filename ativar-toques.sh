#!/data/data/com.termux/files/usr/bin/bash
# Gravalix - libera UMA VEZ a permissão para o app ligar "Mostrar toques" sozinho.
#
# Precisa de: Android 11 ou mais novo, o Gravalix já instalado e Wi-Fi ligado.
# Isso é feito pelo próprio celular, usando a "Depuração sem fio" (não precisa de computador).

echo "=== Gravalix: liberar 'Mostrar toques' ==="

if ! command -v adb >/dev/null 2>&1; then
  echo "Instalando o adb (android-tools)..."
  pkg install -y android-tools || exit 1
fi

cat <<'TXT'

Antes de continuar:
 1) Conecte o celular a uma rede Wi-Fi.
 2) Abra Configurações > Opções do desenvolvedor > Depuração sem fio e LIGUE.
 3) Toque em "Parear dispositivo com código de pareamento".
    Vai aparecer um código de 6 dígitos e um endereço IP:porta.
    DICA: use a tela dividida (Termux de um lado, Configurações do outro),
    porque a janela do código some se você sair dela.
TXT

printf "\nEndereço de PAREAMENTO (ex.: 192.168.0.10:40123): "
read -r PAR
printf "Código de pareamento (6 dígitos): "
read -r COD
adb pair "$PAR" "$COD" || { echo "Não deu para parear. Confira o endereço e o código."; exit 1; }

echo
echo "Agora, na tela principal de 'Depuração sem fio', veja o 'Endereço IP e porta'."
echo "(É outra porta, diferente da do pareamento.)"
printf "Endereço de CONEXÃO (ex.: 192.168.0.10:37891): "
read -r CON
adb connect "$CON" || { echo "Não deu para conectar."; exit 1; }

adb shell pm grant com.gravalix.app android.permission.WRITE_SECURE_SETTINGS
if [ $? -eq 0 ]; then
  echo
  echo "=== Pronto! ==="
  echo "Agora o Gravalix liga e desliga 'Mostrar toques' sozinho."
  echo "Você pode desligar a Depuração sem fio se quiser; a permissão continua."
else
  echo
  echo "Não consegui liberar. O Gravalix está instalado? Tente de novo."
fi
adb disconnect >/dev/null 2>&1
