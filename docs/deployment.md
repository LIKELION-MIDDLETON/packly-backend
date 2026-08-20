# Packly backend deployment

This repository deploys only the Spring backend snapshot. The workflow does not replace the server-managed environment files, AI checkout, model weights, PostgreSQL volume, or other Docker volumes.

## Deployment contract

- Target repository: `LIKELION-MIDDLETON/packly-backend`
- Automatic deployment: every successful push to `main`
- Manual deployment: **Actions → Deploy backend to Packly → Run workflow**
- Server root: `/srv/packly`
- Active backend path: `/srv/packly/backend`
- Server-managed secrets: `/srv/packly/backend.env` and `/srv/packly/ai.env`
- AI files: `/srv/packly/ai`
- Retained backend releases: `/srv/packly/releases/backend/<commit-sha>`; retries add a timestamp suffix
- Compose project: `packly`, defined by `deploy/compose.prod.yaml`
- Photo storage: private S3 via the EC2 instance profile; production Compose fails fast when `S3_PHOTO_BUCKET` is absent

The deploy workflow creates a `git archive`, uploads it to the private bucket under `deploy/backend/`, and invokes `scripts/deploy-backend.sh` through AWS Systems Manager Run Command. GitHub receives short-lived AWS credentials through OIDC; no SSH key or static AWS key is stored in GitHub. On the first managed release, the script builds and starts the complete Compose stack after the server-managed AI files and environment files have been installed. Later releases build only the `api` service, leave PostgreSQL and AI containers untouched, wait for API health, and then recreate the reverse proxy. If activation fails, it restores the previous source link and previous API image when available.

## GitHub configuration

Create a protected GitHub Environment named `production`. Restrict the AWS deploy role trust policy to `repo:LIKELION-MIDDLETON/packly-backend:environment:production`.

Required environment variables:

| Variable | Purpose |
| --- | --- |
| `PACKLY_AWS_DEPLOY_ROLE_ARN` | GitHub OIDC role allowed to upload one release artifact and send one SSM command |
| `PACKLY_AWS_REGION` | `ap-southeast-2` |
| `PACKLY_DEPLOY_BUCKET` | `packly-private-868480224157-ap-southeast-2-an` |
| `PACKLY_INSTANCE_ID` | Packly EC2 instance ID |
| `PACKLY_DEPLOY_HOST` | EC2 public IP or DNS name used by the public health check |

Optional environment variable:

| Variable | Default | Purpose |
| --- | --- | --- |
| `PACKLY_HEALTHCHECK_URL` | `http://<PACKLY_DEPLOY_HOST>/api/health` | Public smoke-test URL; use the HTTPS domain after TLS is configured |

Do not place application secrets, JWT private keys, AI provider keys, database passwords, AWS access keys, SSH private keys, or environment files in GitHub repository contents or build artifacts. Application secrets remain on EC2 in mode `0600`. Runtime S3 access uses the EC2 instance role; deployment uses a separate GitHub OIDC role.

Protect `main` with the **Backend CI / verify** status check and required pull-request review. Restrict production environment deployment to `main` and add a required reviewer if release approval is desired.

## One-time server bootstrap

Copy the bootstrap script to the Ubuntu EC2 instance, inspect it, and run it as root:

```bash
scp -i <key.pem> scripts/bootstrap-server.sh ubuntu@<host>:/tmp/bootstrap-server.sh
ssh -i <key.pem> ubuntu@<host>
sudo DEPLOY_USER=ubuntu /tmp/bootstrap-server.sh
exit
```

Reconnect after bootstrap so Docker group membership takes effect. The script installs Docker and Compose, enables Docker at boot, creates the `/srv/packly` layout, and creates empty mode-`0600` environment files. It never writes secret values.

Populate `/srv/packly/backend.env` and `/srv/packly/ai.env` directly on the server. Before the first backend deployment, place the AI source and `cnn_best.pt` under `/srv/packly/ai`. The first managed backend release starts PostgreSQL, both AI services, Spring, and Nginx together after validating the Compose configuration.

The backend environment must include the following non-secret S3 selectors in addition to its database, JWT, Google, and AI settings:

```dotenv
PHOTO_STORAGE_MODE=s3
AWS_REGION=ap-southeast-2
S3_PHOTO_BUCKET=packly-private-868480224157-ap-southeast-2-an
S3_PHOTO_PREFIX=photos
```

Do not add AWS access keys. The `packly-ec2-role` instance profile supplies short-lived credentials and is restricted to the `photos/`, `backups/`, and read-only `deploy/` object prefixes. The separate GitHub OIDC role may upload only under `deploy/backend/` and send commands only to the Packly EC2 instance.

Subsequent backend deployments deliberately use `--no-deps` for Spring and Nginx; they fail rather than silently rebuilding or replacing PostgreSQL or AI services. AI or infrastructure changes require an explicit full-stack maintenance deployment.

## CI behavior

`.github/workflows/ci.yml` runs on pull requests and `main` pushes. It performs:

1. whitespace and tracked-secret filename checks;
2. lightweight credential-pattern checks over tracked text;
3. Java 17 Gradle tests and `bootJar`;
4. a clean backend Docker image build.

The deployment workflow repeats these checks against the exact revision it will package. No production secret is exposed to its verification job.

## Acceptance checks

After bootstrap and each infrastructure change, verify all of the following:

```bash
# On EC2
docker compose --env-file /srv/packly/backend.env \
  -f /srv/packly/backend/deploy/compose.prod.yaml ps
curl -fsS http://127.0.0.1/api/health
docker ps --format 'table {{.Names}}\t{{.Ports}}'
stat -c '%a %n' /srv/packly/backend.env /srv/packly/ai.env
```

- `api`, `proxy`, `postgres`, `skin-analysis`, and `recommend-api` are healthy.
- Only ports `80/443` are public application ports; `5432`, `8000`, `8001`, and `8080` are not published publicly.
- SSH `22` is reachable only from the approved administrator IP or is replaced by SSM access.
- `/srv/packly/backend.env` and `/srv/packly/ai.env` report mode `600`.
- An anonymous request cannot read objects from the private photo S3 bucket.
- The EC2 instance role, rather than static AWS keys, grants only the required S3 prefix permissions.
- A real Google login, profile completion, survey, image upload, AI analysis, recommendation polling, and recommendation retrieval complete end to end.
- Restarting each container and rebooting EC2 restores the stack without manual intervention.
- PostgreSQL backup restore and a failed-deployment rollback have both been exercised before production use.

The public workflow smoke check proves only that the backend health endpoint is reachable. It does not prove Google OAuth, AI inference, S3 lifecycle behavior, database recovery, or full mobile integration.

## Rollback notes

The deploy script keeps prior release directories and records the running API image before rebuilding. On activation failure it re-points `/srv/packly/backend` to the previous release, re-tags the previous image when available, recreates the API, and waits for health.

The first deployment has no previous release to restore. Verify the prerequisite stack manually before that deployment. Database migrations require their own forward-compatible migration and backup strategy; source/image rollback cannot reverse a database migration.
