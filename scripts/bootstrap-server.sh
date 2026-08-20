#!/usr/bin/env bash
set -Eeuo pipefail

log() {
  printf '[bootstrap] %s\n' "$*"
}

if (( EUID != 0 )); then
  echo 'Run this script as root, for example: sudo ./scripts/bootstrap-server.sh' >&2
  exit 1
fi

deploy_user="${DEPLOY_USER:-ubuntu}"
packly_root="${PACKLY_ROOT:-/srv/packly}"

if [[ "${packly_root}" != /srv/packly ]]; then
  echo 'PACKLY_ROOT must be /srv/packly for the current production Compose contract.' >&2
  exit 1
fi

if ! id "${deploy_user}" >/dev/null 2>&1; then
  echo "Deployment user does not exist: ${deploy_user}" >&2
  exit 1
fi

if [[ ! -r /etc/os-release ]]; then
  echo 'Cannot identify the server operating system.' >&2
  exit 1
fi

# shellcheck disable=SC1091
source /etc/os-release
if [[ "${ID:-}" != ubuntu ]]; then
  echo "This bootstrap is tested for Ubuntu only; detected: ${ID:-unknown}" >&2
  exit 1
fi

install_aws_cli_v2() {
  local architecture=''
  local aws_architecture=''
  local aws_version=''
  local download_dir=''
  local archive_path=''
  local download_url=''

  architecture="$(dpkg --print-architecture)"
  case "${architecture}" in
    amd64)
      aws_architecture='x86_64'
      ;;
    arm64)
      aws_architecture='aarch64'
      ;;
    *)
      echo "Unsupported Ubuntu architecture for AWS CLI v2: ${architecture}" >&2
      return 1
      ;;
  esac

  aws_version="$(aws --version 2>&1 || true)"
  if [[ "${aws_version}" =~ ^aws-cli/2\. ]]; then
    log "AWS CLI v2 is already installed: ${aws_version}"
    return 0
  fi

  download_dir="$(mktemp -d)"
  archive_path="${download_dir}/awscliv2.zip"
  download_url="https://awscli.amazonaws.com/awscli-exe-linux-${aws_architecture}.zip"

  if ! curl --fail --silent --show-error --location --retry 3 --retry-all-errors \
    --proto '=https' --tlsv1.2 --output "${archive_path}" "${download_url}"; then
    rm -rf -- "${download_dir}"
    echo 'Failed to download the AWS CLI v2 installer.' >&2
    return 1
  fi

  if ! unzip -q "${archive_path}" -d "${download_dir}"; then
    rm -rf -- "${download_dir}"
    echo 'Failed to unpack the AWS CLI v2 installer.' >&2
    return 1
  fi

  if ! "${download_dir}/aws/install" \
    --bin-dir /usr/local/bin \
    --install-dir /usr/local/aws-cli \
    --update; then
    rm -rf -- "${download_dir}"
    echo 'Failed to install AWS CLI v2.' >&2
    return 1
  fi

  rm -rf -- "${download_dir}"
  aws_version="$(/usr/local/bin/aws --version 2>&1 || true)"
  if [[ ! "${aws_version}" =~ ^aws-cli/2\. ]]; then
    echo "AWS CLI v2 installation could not be verified: ${aws_version}" >&2
    return 1
  fi
  log "installed AWS CLI v2: ${aws_version}"
}

log 'installing Docker, Compose, curl, unzip, and archive utilities'
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install --yes --no-install-recommends ca-certificates curl docker.io docker-compose-v2 tar unzip
install_aws_cli_v2
systemctl enable --now docker

if snap list amazon-ssm-agent >/dev/null 2>&1; then
  systemctl enable --now snap.amazon-ssm-agent.amazon-ssm-agent.service
else
  snap install amazon-ssm-agent --classic
  systemctl enable --now snap.amazon-ssm-agent.amazon-ssm-agent.service
fi

deploy_group="$(id -gn "${deploy_user}")"
usermod -aG docker "${deploy_user}"

install -d -m 0750 -o "${deploy_user}" -g "${deploy_group}" \
  "${packly_root}" \
  "${packly_root}/ai" \
  "${packly_root}/releases" \
  "${packly_root}/releases/backend"

if [[ ! -e "${packly_root}/backend" ]]; then
  install -d -m 0750 -o "${deploy_user}" -g "${deploy_group}" "${packly_root}/backend"
fi

for env_file in backend.env ai.env; do
  if [[ ! -e "${packly_root}/${env_file}" ]]; then
    install -m 0600 -o "${deploy_user}" -g "${deploy_group}" /dev/null "${packly_root}/${env_file}"
  else
    chown "${deploy_user}:${deploy_group}" "${packly_root}/${env_file}"
    chmod 0600 "${packly_root}/${env_file}"
  fi
done

log "server directories are ready under ${packly_root}"
log "deployment user ${deploy_user} was added to the docker group"
log 'reconnect the SSH session before running Docker without sudo'
log 'populate backend.env and ai.env on the server; this script never writes secret values'
