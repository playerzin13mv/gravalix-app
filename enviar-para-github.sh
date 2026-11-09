#!/data/data/com.termux/files/usr/bin/bash
# Gravalix - envia o projeto para o GitHub pelo Termux.
# Usa só o git que já está vinculado à sua conta (sem pedir login).
# O GitHub então compila o APK sozinho (aba Actions) e publica em Releases.
#
# ANTES: crie um repositório VAZIO no site do GitHub (sem README, sem .gitignore).
#
# Uso:   bash enviar-para-github.sh
#        bash enviar-para-github.sh SEU-USUARIO/Gravalix

cd "$(dirname "$0")" || exit 1

echo "=== Gravalix: enviar para o GitHub ==="

# 1) git instalado?
if ! command -v git >/dev/null 2>&1; then
  echo "Instalando o git..."
  pkg install -y git
fi

# 2) Qual repositório?
ALVO="$1"
if [ -z "$ALVO" ] && git remote get-url origin >/dev/null 2>&1; then
  URL="$(git remote get-url origin)"
  ALVO="$(echo "$URL" | sed -E 's#^.*github\.com[:/]##; s#\.git$##')"
fi
if [ -z "$ALVO" ]; then
  printf "Seu usuário do GitHub: "
  read -r USUARIO
  printf "Nome do repositório [Gravalix]: "
  read -r NOME
  NOME="${NOME:-Gravalix}"
  ALVO="$USUARIO/$NOME"
fi
USUARIO="${ALVO%%/*}"
NOME="${ALVO##*/}"
echo "Repositório: https://github.com/$USUARIO/$NOME"

# 3) Coloca o seu usuário no link de download do README
sed -i "s#SEU-USUARIO#$USUARIO#g; s#/Gravalix/releases#/$NOME/releases#g" README.md

# 4) Identidade do git (só configura se estiver vazia)
if [ -z "$(git config --global user.name)" ]; then
  git config --global user.name "$USUARIO"
fi
if [ -z "$(git config --global user.email)" ]; then
  git config --global user.email "$USUARIO@users.noreply.github.com"
fi

# 5) Commit
if [ ! -d .git ]; then
  git init -b main
fi
git branch -M main
git add -A
git commit -m "Gravalix" || echo "(nada novo para salvar)"

# 6) Liga ao repositório e envia
if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "https://github.com/$USUARIO/$NOME.git"
else
  git remote add origin "https://github.com/$USUARIO/$NOME.git"
fi

if git push -u origin main; then
  echo
  echo "=== Pronto! ==="
  echo "1) Acompanhe a compilação (leva uns 5 minutos):"
  echo "   https://github.com/$USUARIO/$NOME/actions"
  echo "2) Quando ficar verde, baixe o Gravalix.apk em:"
  echo "   https://github.com/$USUARIO/$NOME/releases/latest"
else
  echo
  echo "=== Não consegui enviar ==="
  echo "- O repositório $USUARIO/$NOME existe no GitHub? Crie ele VAZIO no site."
  echo "- Se ele já tem arquivos (README, por exemplo), apague e crie de novo vazio,"
  echo "  ou rode: git push -u origin main --force"
  echo "- Se der erro de permissão com 'workflow', gere um novo token com a opção"
  echo "  'workflow' marcada e use ele no lugar do antigo."
  exit 1
fi
