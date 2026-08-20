#!/usr/bin/env bash
set -Eeuo pipefail

log() {
  printf '[repository-check] %s\n' "$*"
}

base_sha="${BASE_SHA:-}"
head_sha="${HEAD_SHA:-HEAD}"

if [[ -n "${base_sha}" && "${base_sha}" != "0000000000000000000000000000000000000000" ]] \
  && git cat-file -e "${base_sha}^{commit}" 2>/dev/null; then
  log "checking diff whitespace from ${base_sha} to ${head_sha}"
  git diff --check "${base_sha}...${head_sha}"
elif git cat-file -e "${head_sha}^" 2>/dev/null; then
  log "checking diff whitespace for the latest commit"
  git diff --check "${head_sha}^" "${head_sha}"
else
  log "checking working-tree diff whitespace"
  git diff --check
fi

unsafe_files=()
while IFS= read -r path; do
  basename="${path##*/}"
  case "${basename}" in
    .env.example)
      ;;
    .env|.env.*|*.pem|*.key|*.p12|*.pfx)
      unsafe_files+=("${path}")
      ;;
  esac
done < <(git ls-files)

if (( ${#unsafe_files[@]} > 0 )); then
  printf 'Refusing tracked secret-bearing file names:\n' >&2
  printf '  %s\n' "${unsafe_files[@]}" >&2
  exit 1
fi

secret_pattern='(AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9_-]{20,}|-----BEGIN [A-Z ]*PRIVATE KEY-----)'
if git grep -I -nE "${secret_pattern}" -- . ':!scripts/check-repository-safety.sh'; then
  echo 'Potential credential material found in tracked text.' >&2
  exit 1
fi

log "tracked-file and credential sanity checks passed"
