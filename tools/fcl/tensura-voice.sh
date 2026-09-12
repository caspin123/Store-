#!/usr/bin/env bash
# tensura-voice.sh — find and toggle the "Voice of the World" settings of the
# Tensura: Reincarnated voice add-ons (Tensura: Voice, Voice of the World, …).
#
# Forge/NeoForge mods keep their settings in TOML files in two places:
#   <instance>/config/*.toml                     -> client + common settings
#   <instance>/saves/<world>/serverconfig/*.toml -> per-world settings
# This script looks in both, shows the boolean switches, and can flip them.
#
# Usage: ./tensura-voice.sh [--dir PATH] [--instance NAME] [list|enable|disable]

set -Eeuo pipefail

MC_ROOT=${FCL_MINECRAFT:-/storage/emulated/0/FCL/.minecraft}
INSTANCE=""
ACTION="list"

c_grn=$'\033[32m'; c_ylw=$'\033[33m'; c_dim=$'\033[2m'; c_off=$'\033[0m'
[ -t 1 ] || { c_grn=; c_ylw=; c_dim=; c_off=; }
log()  { printf '%s==>%s %s\n' "$c_grn" "$c_off" "$*"; }
die()  { printf '[x] %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<EOF
tensura-voice.sh [options] [list|enable|disable]

  list      Show every voice-related TOML file and its switches (default).
  enable    Turn the matching switches on  (a .bak copy is kept).
  disable   Turn the matching switches off (a .bak copy is kept).

Options
  -d, --dir PATH        FCL .minecraft directory. Default: \$FCL_MINECRAFT
                        or $MC_ROOT
  -i, --instance NAME   Version/instance folder under <.minecraft>/versions.
                        Default: search every instance.
  -h, --help            This text.

Only keys that look like voice switches are touched:
  voice, announce, sound, animation, message, broadcast, render, enable
EOF
}

while [ $# -gt 0 ]; do
  case $1 in
    -d|--dir)      MC_ROOT=${2:?}; shift 2 ;;
    -i|--instance) INSTANCE=${2:?}; shift 2 ;;
    -h|--help)     usage; exit 0 ;;
    list|enable|disable) ACTION=$1; shift ;;
    *) die "Unknown argument: $1 (see --help)" ;;
  esac
done

[ -d "$MC_ROOT" ] || die "Minecraft folder not found: $MC_ROOT
    Pass the right path with --dir, e.g. --dir /storage/emulated/0/FCL/.minecraft"

# Where to look for config folders.
ROOTS=()
if [ -n "$INSTANCE" ]; then
  [ -d "$MC_ROOT/versions/$INSTANCE" ] || die "No such instance: $MC_ROOT/versions/$INSTANCE"
  ROOTS+=("$MC_ROOT/versions/$INSTANCE")
else
  ROOTS+=("$MC_ROOT")
  while IFS= read -r d; do ROOTS+=("$d"); done < <(find "$MC_ROOT/versions" -mindepth 1 -maxdepth 1 -type d 2>/dev/null || true)
fi

# Voice-related config files: by file name, or by content mentioning "voice".
FILES=()
for root in "${ROOTS[@]}"; do
  for dir in "$root/config" "$root/saves"; do
    [ -d "$dir" ] || continue
    while IFS= read -r f; do
      case $(basename "$f" | tr '[:upper:]' '[:lower:]') in
        *voice*|*tensura*) FILES+=("$f"); continue ;;
      esac
      if grep -qiE '(voice|world.?announce)' "$f" 2>/dev/null; then FILES+=("$f"); fi
    done < <(find "$dir" -type f -name '*.toml' 2>/dev/null)
  done
done

if [ ${#FILES[@]} -eq 0 ]; then
  cat >&2 <<EOF
[!] No voice-related config found under $MC_ROOT

That usually means one of:
  * the voice add-on jar is not in the mods folder yet, or
  * you have not launched the world once — Forge/NeoForge writes the TOML
    files on the first run (per-world files appear only after the world
    is created), or
  * you are looking at the wrong instance (try --instance NAME).
EOF
  exit 1
fi

KEY_RE='(voice|announce|sound|animation|message|broadcast|render|enable)'

show_file() {
  local f=$1 hits
  hits=$(grep -nE "^[[:space:]]*[A-Za-z_][A-Za-z0-9_]*[[:space:]]*=[[:space:]]*(true|false)" "$f" \
         | grep -iE "$KEY_RE" || true)
  printf '\n%s%s%s\n' "$c_ylw" "${f#"$MC_ROOT"/}" "$c_off"
  if [ -n "$hits" ]; then
    printf '%s\n' "$hits" | sed 's/^/    /'
  else
    printf '    %s(no boolean switches in this file)%s\n' "$c_dim" "$c_off"
  fi
}

flip() { # flip <file> <from> <to>
  local f=$1 from=$2 to=$3 changed
  cp -f "$f" "$f.bak"
  changed=$(awk -v from="$from" -v to="$to" -v keyre="$KEY_RE" '
    {
      line = $0
      if (line ~ /^[[:space:]]*[A-Za-z_][A-Za-z0-9_]*[[:space:]]*=[[:space:]]*(true|false)[[:space:]]*$/) {
        split(line, kv, "=")
        key = tolower(kv[1])
        gsub(/[[:space:]]/, "", key)
        val = kv[2]
        gsub(/[[:space:]]/, "", val)
        if (key ~ keyre && val == from) {
          sub(/=[[:space:]]*(true|false)[[:space:]]*$/, "= " to, line)
          n++
        }
      }
      print line > out
    }
    END { print n + 0 }
  ' out="$f.tmp" "$f")
  mv -f "$f.tmp" "$f"
  printf '%s' "$changed"
}

case $ACTION in
  list)
    log "Voice config files under $MC_ROOT"
    for f in "${FILES[@]}"; do show_file "$f"; done
    printf '\n%sRun with "enable" to switch every option above to true.%s\n' "$c_dim" "$c_off"
    ;;
  enable|disable)
    if [ "$ACTION" = enable ]; then from=false; to=true; else from=true; to=false; fi
    total=0
    for f in "${FILES[@]}"; do
      n=$(flip "$f" "$from" "$to")
      total=$((total + n))
      if [ "$n" -gt 0 ]; then
        printf '  %s: %s option(s) -> %s\n' "${f#"$MC_ROOT"/}" "$n" "$to"
      fi
    done
    log "$total option(s) changed to $to. Backups kept as *.toml.bak"
    printf '%sRestart Minecraft for the change to take effect.%s\n' "$c_dim" "$c_off"
    printf '%sOn a multiplayer server the SERVER config wins — ask an admin.%s\n' "$c_dim" "$c_off"
    ;;
esac
