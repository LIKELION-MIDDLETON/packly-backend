#!/usr/bin/env bash
set -Eeuo pipefail

log() {
  printf '[deploy-backend] %s\n' "$*"
}

die() {
  printf '[deploy-backend] ERROR: %s\n' "$*" >&2
  exit 1
}

packly_root="${PACKLY_ROOT:-/srv/packly}"
release_sha="${RELEASE_SHA:?RELEASE_SHA is required}"
archive_path="${ARCHIVE_PATH:?ARCHIVE_PATH is required}"
health_timeout="${HEALTH_TIMEOUT_SECONDS:-240}"
backend_path="${packly_root}/backend"
release_root="${packly_root}/releases/backend"
release_path="${release_root}/${release_sha}"
env_file="${packly_root}/backend.env"
previous_source=''
previous_image=''
source_switched=false
new_release_path=''
initial_stack_started=false
has_previous_release=false

[[ "${packly_root}" == /srv/packly ]] || die 'PACKLY_ROOT must be /srv/packly.'
[[ "${release_sha}" =~ ^[0-9a-f]{7,64}$ ]] || die 'RELEASE_SHA must be a hexadecimal Git revision.'
[[ "${health_timeout}" =~ ^[0-9]+$ ]] || die 'HEALTH_TIMEOUT_SECONDS must be a positive integer.'
(( health_timeout > 0 )) || die 'HEALTH_TIMEOUT_SECONDS must be greater than zero.'
[[ -f "${archive_path}" ]] || die "Deployment archive does not exist: ${archive_path}"
[[ -f "${env_file}" ]] || die "Server-managed environment file is missing: ${env_file}"
command -v docker >/dev/null 2>&1 || die 'Docker is not installed.'
docker compose version >/dev/null 2>&1 || die 'Docker Compose v2 is not installed.'
command -v flock >/dev/null 2>&1 || die 'flock is required for deployment serialization.'

exec 9>"${packly_root}/.backend-deploy.lock"
flock -n 9 || die 'Another backend deployment is already running.'

if [[ "$(stat -c '%a' "${env_file}")" != 600 ]]; then
  die "${env_file} must have mode 600."
fi

if tar -tzf "${archive_path}" | grep -Eq '(^/|(^|/)\.\.(/|$))'; then
  die 'Deployment archive contains an unsafe path.'
fi

wait_for_healthy() {
  local compose_file="$1"
  local service="$2"
  local deadline=$((SECONDS + health_timeout))
  local container_id=''
  local status=''

  while (( SECONDS < deadline )); do
    container_id="$(docker compose --env-file "${env_file}" -f "${compose_file}" ps -q "${service}")"
    if [[ -n "${container_id}" ]]; then
      status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${container_id}")"
      case "${status}" in
        healthy|running)
          log "${service} reached ${status} state"
          return 0
          ;;
        unhealthy|exited|dead)
          docker logs --tail 100 "${container_id}" >&2 || true
          return 1
          ;;
      esac
    fi
    sleep 5
  done

  if [[ -n "${container_id}" ]]; then
    docker logs --tail 100 "${container_id}" >&2 || true
  fi
  echo "Timed out waiting for ${service} health." >&2
  return 1
}

rollback() {
  local exit_code=$?
  trap - ERR
  set +e
  log "deployment failed; attempting rollback"

  if [[ "${initial_stack_started}" == true && -n "${new_release_path}" ]]; then
    local initial_compose="${new_release_path}/deploy/compose.prod.yaml"
    if [[ -f "${initial_compose}" ]]; then
      docker compose --env-file "${env_file}" -f "${initial_compose}" down || true
      log 'stopped containers created by the failed first deployment; persistent volumes were retained'
    fi
  fi

  if [[ "${source_switched}" == true ]]; then
    if [[ -n "${previous_source}" && -d "${previous_source}" ]]; then
      local rollback_link="${packly_root}/.backend-rollback-${release_sha}"
      rm -f -- "${rollback_link}"
      ln -s "${previous_source}" "${rollback_link}"
      mv -Tf "${rollback_link}" "${backend_path}"
      log "restored previous backend source: ${previous_source}"

      local previous_compose="${backend_path}/deploy/compose.prod.yaml"
      if [[ -n "${previous_image}" ]] && docker image inspect "${previous_image}" >/dev/null 2>&1; then
        docker tag "${previous_image}" packly-api:latest
      elif [[ -f "${previous_compose}" ]]; then
        docker compose --env-file "${env_file}" -f "${previous_compose}" build api || true
      fi

      if [[ -f "${previous_compose}" ]]; then
        docker compose --env-file "${env_file}" -f "${previous_compose}" \
          up -d --no-deps --force-recreate api || true
        wait_for_healthy "${previous_compose}" api || true
        docker compose --env-file "${env_file}" -f "${previous_compose}" \
          up -d --no-deps --force-recreate proxy || true
      fi
    else
      rm -f "${backend_path}"
      install -d -m 0750 "${backend_path}"
      log 'no previous managed release was available to restore'
    fi
  elif [[ -n "${previous_source}" && ! -e "${backend_path}" && -d "${previous_source}" ]]; then
    local interrupted_link="${packly_root}/.backend-interrupted-${release_sha}"
    rm -f -- "${interrupted_link}"
    ln -s "${previous_source}" "${interrupted_link}"
    mv -Tf "${interrupted_link}" "${backend_path}"
    log "restored previous backend source after interrupted activation: ${previous_source}"
  fi

  if [[ -n "${new_release_path}" && -d "${new_release_path}" ]]; then
    rm -rf -- "${new_release_path}"
  fi
  rm -f -- "${archive_path}"
  exit "${exit_code}"
}

trap rollback ERR

mkdir -p "${release_root}"
if [[ -e "${release_path}" ]]; then
  release_path="${release_root}/${release_sha}-retry-$(date -u +%Y%m%dT%H%M%SZ)-$$"
  log "using retry release path: ${release_path}"
fi

new_release_path="${release_path}"
mkdir "${release_path}"
tar -xzf "${archive_path}" -C "${release_path}"
[[ -f "${release_path}/Dockerfile" ]] || die 'Snapshot does not contain Dockerfile.'
[[ -f "${release_path}/deploy/compose.prod.yaml" ]] || die 'Snapshot does not contain deploy/compose.prod.yaml.'

if [[ -L "${backend_path}" ]]; then
  previous_source="$(readlink -f "${backend_path}")"
elif [[ -d "${backend_path}" ]]; then
  previous_source="${release_root}/pre-managed-$(date -u +%Y%m%dT%H%M%SZ)"
  mv "${backend_path}" "${previous_source}"
elif [[ -e "${backend_path}" ]]; then
  die "Backend path is neither a directory nor a symlink: ${backend_path}"
fi

if [[ -n "${previous_source}" && -f "${previous_source}/deploy/compose.prod.yaml" ]]; then
  has_previous_release=true
  previous_image="$(docker compose --env-file "${env_file}" \
    -f "${previous_source}/deploy/compose.prod.yaml" images -q api 2>/dev/null | head -n 1 || true)"
fi

new_link="${packly_root}/.backend-release-${release_sha}"
ln -s "${release_path}" "${new_link}"
mv -Tf "${new_link}" "${backend_path}"
source_switched=true

compose_file="${backend_path}/deploy/compose.prod.yaml"
docker compose --env-file "${env_file}" -f "${compose_file}" config --quiet
if [[ "${has_previous_release}" == false ]]; then
  log "building the complete stack for first deployment ${release_sha}"
  docker compose --env-file "${env_file}" -f "${compose_file}" build
  initial_stack_started=true
  docker compose --env-file "${env_file}" -f "${compose_file}" up -d
  for service in postgres skin-analysis recommend-api api proxy; do
    wait_for_healthy "${compose_file}" "${service}"
  done
else
  log "building API image for ${release_sha}"
  docker compose --env-file "${env_file}" -f "${compose_file}" build api

  log 'starting API without changing PostgreSQL or AI containers'
  docker compose --env-file "${env_file}" -f "${compose_file}" \
    up -d --no-deps --force-recreate api
  wait_for_healthy "${compose_file}" api

  log 'starting reverse proxy after API health passes'
  docker compose --env-file "${env_file}" -f "${compose_file}" \
    up -d --no-deps --force-recreate proxy
  wait_for_healthy "${compose_file}" proxy
fi
curl --fail --silent --show-error --max-time 10 http://127.0.0.1/api/health >/dev/null

trap - ERR

prune_releases() {
  local current_source=''
  local kept=0
  local release_dir=''

  current_source="$(readlink -f "${backend_path}")"
  while IFS= read -r release_dir; do
    [[ -n "${release_dir}" ]] || continue
    if [[ "${release_dir}" == "${current_source}" || "${release_dir}" == "${previous_source}" ]]; then
      continue
    fi
    if (( kept < 3 )); then
      kept=$((kept + 1))
      continue
    fi
    log "removing old backend release: ${release_dir}"
    rm -rf -- "${release_dir}"
  done < <(
    find "${release_root}" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' \
      | sort -rn \
      | cut -d' ' -f2-
  )
}

prune_releases
rm -f -- "${archive_path}"
log "deployment completed for ${release_sha}"
