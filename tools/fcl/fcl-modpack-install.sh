#!/usr/bin/env bash
# fcl-modpack-install.sh
#
# Installs a CurseForge modpack (client .zip with a manifest.json) into a
# Fold Craft Launcher (FCL) instance on Android/Termux — or any Linux box.
#
# Tuned for: Official Tensura: Reincarnated Survival Server (MC 1.21.1 / NeoForge)
# but works with any CurseForge pack that ships the standard manifest format.
#
# Usage:  ./fcl-modpack-install.sh [options] <modpack.zip>
# Help:   ./fcl-modpack-install.sh --help

set -Eeuo pipefail

SCRIPT_NAME=$(basename "$0")

# ---------------------------------------------------------------- defaults ---
MC_ROOT=${FCL_MINECRAFT:-/storage/emulated/0/FCL/.minecraft}
INSTANCE_NAME=""
API_KEY=${CURSEFORGE_API_KEY:-}
JOBS=4
EXCLUDE_FILE=""
SHARED=0
DRY_RUN=0
KEEP_TEMP=0
ZIP_PATH=""

CF_API="https://api.curseforge.com/v1"
CF_WEB="https://www.curseforge.com/api/v1"

# ----------------------------------------------------------------- helpers ---
c_red=$'\033[31m'; c_grn=$'\033[32m'; c_ylw=$'\033[33m'; c_dim=$'\033[2m'; c_off=$'\033[0m'
[ -t 1 ] || { c_red=; c_grn=; c_ylw=; c_dim=; c_off=; }

log()  { printf '%s==>%s %s\n' "$c_grn" "$c_off" "$*"; }
warn() { printf '%s[!]%s %s\n' "$c_ylw" "$c_off" "$*" >&2; }
die()  { printf '%s[x]%s %s\n' "$c_red" "$c_off" "$*" >&2; exit 1; }
dim()  { printf '%s    %s%s\n' "$c_dim" "$*" "$c_off"; }

usage() {
  cat <<EOF
$SCRIPT_NAME — install a CurseForge modpack into Fold Craft Launcher

  $SCRIPT_NAME [options] <modpack.zip>

Options
  -n, --name NAME       Instance (version) folder name. Default: from manifest.
  -d, --dir PATH        FCL .minecraft directory.
                        Default: \$FCL_MINECRAFT or $MC_ROOT
  -k, --api-key KEY     CurseForge API key. Default: \$CURSEFORGE_API_KEY.
                        Without it the script falls back to the public
                        download endpoint (slower, best-effort).
  -j, --jobs N          Parallel downloads. Default: $JOBS.
  -x, --exclude FILE    File with one pattern per line; matching mod file
                        names are skipped. Lines starting with # are ignored.
      --shared          Install into <.minecraft>/mods instead of a per-version
                        folder (only if you do NOT use version isolation).
      --dry-run         Resolve everything, download nothing.
      --keep-temp       Keep the extracted pack for inspection.
  -h, --help            This text.

Notes
  * Re-running is safe: existing, complete mod files are skipped, so an
    interrupted run just continues where it stopped.
  * The Minecraft + mod loader versions are printed at the end; install that
    loader inside FCL with the exact same instance name.
EOF
}

need() { command -v "$1" >/dev/null 2>&1; }

require_tools() {
  local missing=()
  need unzip || missing+=(unzip)
  need curl  || missing+=(curl)
  if ! need jq && ! need python3; then missing+=(jq); fi
  if [ ${#missing[@]} -gt 0 ]; then
    die "Missing tools: ${missing[*]}
    Termux:  pkg install ${missing[*]}
    Debian:  sudo apt install ${missing[*]}"
  fi
}

# json_path <file> <jq-filter> <python-expr>   -- jq first, python3 as fallback
json_read() {
  local file=$1 filter=$2 pyexpr=$3
  if need jq; then
    jq -r "$filter" "$file"
  else
    python3 -c "
import json,sys
d=json.load(open(sys.argv[1]))
$pyexpr
" "$file"
  fi
}

# ------------------------------------------------------------------- parse ---
while [ $# -gt 0 ]; do
  case $1 in
    -n|--name)     INSTANCE_NAME=${2:?}; shift 2 ;;
    -d|--dir)      MC_ROOT=${2:?}; shift 2 ;;
    -k|--api-key)  API_KEY=${2:?}; shift 2 ;;
    -j|--jobs)     JOBS=${2:?}; shift 2 ;;
    -x|--exclude)  EXCLUDE_FILE=${2:?}; shift 2 ;;
    --shared)      SHARED=1; shift ;;
    --dry-run)     DRY_RUN=1; shift ;;
    --keep-temp)   KEEP_TEMP=1; shift ;;
    -h|--help)     usage; exit 0 ;;
    -*)            die "Unknown option: $1 (see --help)" ;;
    *)             [ -z "$ZIP_PATH" ] || die "Only one modpack zip at a time"
                   ZIP_PATH=$1; shift ;;
  esac
done

[ -n "$ZIP_PATH" ] || { usage; exit 1; }
[ -f "$ZIP_PATH" ] || die "Modpack zip not found: $ZIP_PATH"
case $JOBS in ''|*[!0-9]*) die "--jobs must be a number" ;; esac
[ "$JOBS" -ge 1 ] || JOBS=1

require_tools

# ----------------------------------------------------------------- extract ---
TMP_DIR=$(mktemp -d "${TMPDIR:-/tmp}/fclpack.XXXXXX")
cleanup() { [ "$KEEP_TEMP" = 1 ] || rm -rf "$TMP_DIR"; }
trap cleanup EXIT

log "Extracting $(basename "$ZIP_PATH")"
unzip -q -o "$ZIP_PATH" -d "$TMP_DIR" || die "Could not extract the zip (corrupt download?)"

MANIFEST="$TMP_DIR/manifest.json"
if [ ! -f "$MANIFEST" ]; then
  # Some packs nest everything one level deep.
  found=$(find "$TMP_DIR" -maxdepth 3 -name manifest.json -print -quit || true)
  [ -n "$found" ] || die "No manifest.json inside the zip — is this the CLIENT pack (not the server pack)?"
  MANIFEST=$found
  TMP_DIR=$(dirname "$found")
fi

PACK_NAME=$(json_read "$MANIFEST" '.name // "modpack"' 'print(d.get("name","modpack"))')
PACK_VERSION=$(json_read "$MANIFEST" '.version // ""' 'print(d.get("version",""))')
MC_VERSION=$(json_read "$MANIFEST" '.minecraft.version' 'print(d["minecraft"]["version"])')
LOADER_ID=$(json_read "$MANIFEST" \
  '[.minecraft.modLoaders[] | select(.primary == true) | .id][0] // .minecraft.modLoaders[0].id' \
  'ls=d["minecraft"]["modLoaders"]; p=[x for x in ls if x.get("primary")] or ls; print(p[0]["id"])')

[ -n "$MC_VERSION" ] || die "manifest.json has no Minecraft version"

if [ -z "$INSTANCE_NAME" ]; then
  INSTANCE_NAME=$(printf '%s %s' "$PACK_NAME" "$PACK_VERSION" | tr -s ' ' '-' | tr -cd '[:alnum:]._-')
  INSTANCE_NAME=${INSTANCE_NAME%-}
fi

if [ "$SHARED" = 1 ]; then
  GAME_DIR="$MC_ROOT"
else
  GAME_DIR="$MC_ROOT/versions/$INSTANCE_NAME"
fi
MODS_DIR="$GAME_DIR/mods"

log "Pack      : $PACK_NAME ${PACK_VERSION:+($PACK_VERSION)}"
log "Minecraft : $MC_VERSION"
log "Loader    : $LOADER_ID"
log "Instance  : $INSTANCE_NAME"
log "Game dir  : $GAME_DIR"

# --------------------------------------------------------------- mod list ----
MODLIST="$TMP_DIR/.modlist"
json_read "$MANIFEST" \
  '.files[] | "\(.projectID) \(.fileID)"' \
  'print("\n".join("%s %s" % (f["projectID"], f["fileID"]) for f in d.get("files",[])))' \
  | sed '/^$/d' > "$MODLIST"

TOTAL=$(wc -l < "$MODLIST" | tr -d ' ')
log "Mods in manifest: $TOTAL"
[ "$TOTAL" -gt 0 ] || warn "The manifest lists no mods — the pack may ship everything inside overrides/"

# Excludes
EXCLUDE_PATTERNS=""
if [ -n "$EXCLUDE_FILE" ]; then
  [ -f "$EXCLUDE_FILE" ] || die "Exclude file not found: $EXCLUDE_FILE"
  count=0
  while IFS= read -r line || [ -n "$line" ]; do
    line=${line%%#*}
    line=$(printf '%s' "$line" | tr -d '\r' | tr '[:upper:]' '[:lower:]' \
           | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    if [ -n "$line" ]; then
      EXCLUDE_PATTERNS="$EXCLUDE_PATTERNS$line
"
      count=$((count + 1))
    fi
  done < "$EXCLUDE_FILE"
  log "Exclude patterns: $count"
fi

# Patterns travel to the download workers through the environment, so they
# must be a plain newline-separated string rather than an array.
is_excluded() {
  local name_lc pat
  [ -n "${EXCLUDE_PATTERNS:-}" ] || return 1
  name_lc=$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')
  while IFS= read -r pat; do
    [ -n "$pat" ] || continue
    case $name_lc in *"$pat"*) return 0 ;; esac
  done <<< "$EXCLUDE_PATTERNS"
  return 1
}

# ------------------------------------------------------------------ fetch ----
if [ "$DRY_RUN" = 0 ]; then
  mkdir -p "$MODS_DIR" || die "Cannot write to $MODS_DIR
    On Android run 'termux-setup-storage' first and grant file access."
fi

curl_api() { # curl_api <url>
  curl -fsSL --retry 4 --retry-delay 2 --retry-connrefused --max-time 60 \
       -H "Accept: application/json" ${API_KEY:+-H "x-api-key: $API_KEY"} "$1"
}

# Prints "<fileName>\t<downloadUrl>\t<fileLength>" or nothing.
resolve_file() { # resolve_file <projectID> <fileID>
  local pid=$1 fid=$2 json
  [ -n "$API_KEY" ] || return 1
  json=$(curl_api "$CF_API/mods/$pid/files/$fid" 2>/dev/null) || return 1
  printf '%s' "$json" | {
    if need jq; then
      jq -r '.data | [.fileName, (.downloadUrl // ""), (.fileLength // 0)] | @tsv'
    else
      python3 -c "
import json,sys
d=json.load(sys.stdin)['data']
print('\t'.join([d['fileName'], d.get('downloadUrl') or '', str(d.get('fileLength') or 0)]))"
    fi
  }
}

# CurseForge CDN layout for files whose downloadUrl is withheld.
cdn_url() { # cdn_url <fileID> <fileName>
  local fid=$1 name=$2
  printf 'https://mediafilez.forgecdn.net/files/%s/%s/%s' \
    "${fid:0:4}" "$((10#${fid: -3}))" "$name"
}

# Resolve the real file name by following the public download redirect.
resolve_name_via_redirect() { # <url>
  local eff
  eff=$(curl -fsIL -o /dev/null -w '%{url_effective}' --max-time 60 "$1" 2>/dev/null) || return 1
  eff=${eff%%\?*}
  eff=${eff##*/}
  [ -n "$eff" ] || return 1
  printf '%s' "$eff" | sed 's/%20/ /g; s/%2B/+/g'
}

download_one() { # download_one <projectID> <fileID>
  local pid=$1 fid=$2 meta name url size target have
  name=""; size=0

  if meta=$(resolve_file "$pid" "$fid"); then
    name=$(printf '%s' "$meta" | cut -f1)
    url=$(printf '%s' "$meta" | cut -f2)
    size=$(printf '%s' "$meta" | cut -f3)
    [ -n "$url" ] || url=$(cdn_url "$fid" "$name")
  else
    url="$CF_WEB/mods/$pid/files/$fid/download"
    name=$(resolve_name_via_redirect "$url") || name=""
  fi
  case $size in ''|*[!0-9]*) size=0 ;; esac

  if [ -n "$name" ]; then
    if is_excluded "$name"; then
      printf 'skip-excluded %s\n' "$name"; return 0
    fi
    target="$MODS_DIR/$name"
    if [ -s "$target" ]; then
      have=$(wc -c < "$target" | tr -d ' ')
      if [ "$size" -eq 0 ] || [ "$have" = "$size" ]; then
        printf 'skip-present %s\n' "$name"; return 0
      fi
    fi
  fi

  if [ "$DRY_RUN" = 1 ]; then printf 'dry-run %s\n' "${name:-$pid/$fid}"; return 0; fi

  if [ -n "$name" ]; then
    if curl -fsSL --retry 4 --retry-delay 2 --retry-connrefused --max-time 900 \
            -o "$target.part" "$url"; then
      mv -f "$target.part" "$target"
      printf 'ok %s\n' "$name"
    else
      rm -f "$target.part"
      printf 'FAIL %s\n' "$name"
    fi
  else
    # Last resort: let curl take the name from Content-Disposition.
    if ( cd "$MODS_DIR" && curl -fsSL --retry 4 --retry-delay 2 --max-time 900 -OJ "$url" ); then
      printf 'ok %s\n' "$pid/$fid"
    else
      printf 'FAIL %s (could not resolve a file name)\n' "$pid/$fid"
    fi
  fi
}

export -f download_one resolve_file resolve_name_via_redirect cdn_url curl_api is_excluded need
export MODS_DIR API_KEY DRY_RUN CF_API CF_WEB EXCLUDE_PATTERNS

FAILED=0
if [ "$TOTAL" -gt 0 ]; then
  log "Downloading mods (${JOBS}x parallel)…"
  [ -n "$API_KEY" ] || warn "No CurseForge API key — falling back to the public endpoint. Set CURSEFORGE_API_KEY for faster, verifiable downloads."

  RESULTS="$TMP_DIR/.results"
  : > "$RESULTS"
  xargs -P "$JOBS" -n 2 bash -c 'download_one "$0" "$1"' < "$MODLIST" \
    | tee -a "$RESULTS" \
    | while IFS= read -r line; do
        case $line in
          FAIL*) printf '  %s%s%s\n' "$c_red" "$line" "$c_off" ;;
          skip*) printf '  %s%s%s\n' "$c_dim" "$line" "$c_off" ;;
          *)     printf '  %s\n' "$line" ;;
        esac
      done

  FAILED=$(grep -c '^FAIL ' "$RESULTS" || true)
fi

# -------------------------------------------------------------- overrides ----
OVERRIDES_NAME=$(json_read "$MANIFEST" '.overrides // "overrides"' 'print(d.get("overrides","overrides"))')
if [ -d "$TMP_DIR/$OVERRIDES_NAME" ]; then
  log "Copying overrides (config, kubejs, resourcepacks, …)"
  if [ "$DRY_RUN" = 0 ]; then
    mkdir -p "$GAME_DIR"
    if need rsync; then
      rsync -a "$TMP_DIR/$OVERRIDES_NAME/" "$GAME_DIR/"
    else
      cp -a "$TMP_DIR/$OVERRIDES_NAME/." "$GAME_DIR/"
    fi
  fi
else
  warn "No '$OVERRIDES_NAME' folder in the pack"
fi

# ----------------------------------------------------------------- report ----
if [ "$DRY_RUN" = 0 ]; then
  INSTALLED=$(find "$MODS_DIR" -maxdepth 1 -name '*.jar' 2>/dev/null | wc -l | tr -d ' ')
  cat > "$GAME_DIR/fcl-instance-info.txt" <<EOF
pack        = $PACK_NAME $PACK_VERSION
minecraft   = $MC_VERSION
mod_loader  = $LOADER_ID
instance    = $INSTANCE_NAME
mods_in_manifest = $TOTAL
jars_present     = $INSTALLED
installed_at     = $(date -u '+%Y-%m-%dT%H:%M:%SZ')
EOF
else
  INSTALLED="(dry-run)"
fi

echo
log "Done."
dim "mods installed : $INSTALLED / $TOTAL"
if [ "${FAILED:-0}" -gt 0 ]; then
  warn "$FAILED mod(s) failed - re-run the same command to retry only those."
fi

cat <<EOF

${c_grn}Next steps in FCL${c_off}
  1. Settings → enable ${c_ylw}version isolation${c_off} (each version keeps its own mods folder).
  2. Install ${c_ylw}$LOADER_ID${c_off} for Minecraft ${c_ylw}$MC_VERSION${c_off}
     and name the version exactly: ${c_ylw}$INSTANCE_NAME${c_off}
  3. Re-run this script afterwards if you installed the loader second —
     already-downloaded mods are skipped.
  4. Java 21, 3–4 GB RAM, renderer with OpenGL 3.2+ (Zink / Turnip on Adreno).
EOF
