#!/usr/bin/env bash
# Limpa o que a versão anterior deixou para trás.
#
# Rodar SEMPRE ao gerar uma versão nova do app, depois de copiar o APK:
#
#   tools/limpar-versao.sh [quantas-capturas-manter]
#
# O que sai:
#   1. APKs antigos da raiz — fica só o mais recente (NexTasks-<versão>-debug.apk).
#   2. Capturas de teste antigas (as que eu tiro do emulador para conferir a tela).
#      Ficam as N mais recentes (padrão 6), que são as da versão atual.
#   3. Snapshots do AVD, se existirem.
#   4. A Lixeira, no fim.
#
# Importante: neste ambiente `rm` é uma função que manda arquivos para a Lixeira.
# Para liberar espaço de verdade é preciso o binário (`/usr/bin/rm`) — senão o
# arquivo só muda de lugar e o disco continua cheio.
#
# O que NÃO sai (de propósito):
#   - app/build: apagar força build completo na próxima vez, e são só ~70 MB.
#   - Caches do Gradle (~1 GB): custam download de novo.
#   - Qualquer arquivo do projeto que não seja APK.

set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SHOTS="${SHOTS_DIR:-$HOME/Downloads/toolchain}"
MANTER_SHOTS="${1:-6}"
SNAP_DIR="$HOME/.android/avd/NexTasks_API36.avd/snapshots"
RM="/usr/bin/rm"          # binário: apaga de verdade, não manda para a Lixeira

cd "$ROOT" || exit 1

livre_antes="$(powershell -NoProfile -Command "[math]::Round((Get-PSDrive C).Free/1GB,2)" 2>/dev/null | tr -d '\r')"

# --- 1. APKs: só o mais recente fica -----------------------------------------
antigos="$(ls -t NexTasks-*-debug.apk 2>/dev/null | tail -n +2)"
if [ -n "$antigos" ]; then
  echo "$antigos" | while IFS= read -r f; do
    "$RM" -f -- "$f" && echo "apagado: $f"
  done
else
  echo "APKs: nada antigo para apagar"
fi

# --- 2. Capturas de teste ----------------------------------------------------
if [ -d "$SHOTS" ]; then
  total_antes="$(ls "$SHOTS"/*.png 2>/dev/null | wc -l | tr -d ' ')"
  if [ "$total_antes" -gt "$MANTER_SHOTS" ]; then
    ls -t "$SHOTS"/*.png 2>/dev/null | tail -n +$((MANTER_SHOTS + 1)) | while IFS= read -r f; do
      "$RM" -f -- "$f"
    done
    total_depois="$(ls "$SHOTS"/*.png 2>/dev/null | wc -l | tr -d ' ')"
    echo "capturas: $total_antes -> $total_depois (mantidas as $MANTER_SHOTS mais recentes)"
  else
    echo "capturas: nada antigo para apagar ($total_antes)"
  fi
fi

# --- 3. Snapshots do AVD -----------------------------------------------------
# O emulador grava ~2,5 GB a cada sessão e manda o antigo para a Lixeira.
# O script do atalho já usa -no-snapshot; isto limpa o que sobrou de antes.
if [ -d "$SNAP_DIR" ] && [ -n "$(ls -A "$SNAP_DIR" 2>/dev/null)" ]; then
  "$RM" -rf -- "$SNAP_DIR"/* 2>/dev/null
  echo "snapshots do AVD: apagados"
fi

# --- 4. Lixeira --------------------------------------------------------------
# Só faz sentido no fim: se algo acima foi para lá, sai junto.
powershell -NoProfile -Command "Clear-RecycleBin -DriveLetter C -Force -ErrorAction SilentlyContinue" >/dev/null 2>&1

livre_depois="$(powershell -NoProfile -Command "[math]::Round((Get-PSDrive C).Free/1GB,2)" 2>/dev/null | tr -d '\r')"
echo "---"
echo "APK na raiz: $(ls NexTasks-*.apk 2>/dev/null | tr '\n' ' ')"
if [ -n "$livre_antes" ] && [ -n "$livre_depois" ]; then
  echo "Disco livre: ${livre_antes} GB -> ${livre_depois} GB"
fi
