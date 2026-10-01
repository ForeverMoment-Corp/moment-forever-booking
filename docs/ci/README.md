# Booking Service CI/CD Guide

This document explains the CI pipeline for the Booking service in beginner-friendly terms and keeps **CI and CD/deploy clearly separate**.

## WHAT this pipeline does

The workflow file is:

- `.github/workflows/ci.yml`

It runs on:

- `push` to `main`
- `pull_request` targeting `main`

Main stages:

1. **Checkout** source code.
2. **Set up Java 17** with Maven dependency cache.
3. **Build + test** with Maven (`clean verify`).
4. **Security scan placeholder** (currently a no-op informational step).
5. **Docker build** (using repository `Dockerfile`).
6. **Docker Hub login** using secrets:
   - `DOCKERHUB_USERNAME`
   - `DOCKERHUB_TOKEN`
7. **Docker push with immutable SHA tag**:
   - `<dockerhub_repo>:${{ github.sha }}`
8. **Optional latest tag update on main** (toggle via `PUSH_LATEST_ON_MAIN`).

## WHY this is useful

- **Fast feedback:** build/test failures are caught before merge.
- **Repeatable builds:** same steps run for every change.
- **Traceable images:** SHA tags are immutable and map directly to one commit.
- **Safer delivery foundation:** CI artifact publishing is automated without mixing deployment logic.

## HOW it works

### 1) Build pipeline (Java + Maven)

- The job checks if `./mvnw` exists.
- If present, it uses wrapper (`./mvnw`); otherwise it falls back to `mvn`.
- It runs:
  - `clean verify`

This validates compile + tests in one CI execution path.

### 2) Testing pipeline

- Tests run as part of `verify`.
- If tests fail, downstream Docker publish job does not run.

### 3) Docker publishing pipeline

- Runs only for `push` events (not PR events).
- Builds image from root-level `Dockerfile`.
- Logs in to Docker Hub using repo secrets.
- Pushes immutable SHA tag:
  - `<dockerhub_repo>:${{ github.sha }}`
- Optionally also pushes `latest` only on `main`.

### 4) CI/CD separation (strict)

This workflow does **not** include:

- Kubernetes commands (`kubectl`)
- SSH/VM deployment
- Helm release/upgrade
- Any runtime environment deploy step

It is CI plus image publishing only.

## HOW TO VERIFY

### Local pre-checks (optional)

From repository root:

```bash
./mvnw clean verify
docker build -f Dockerfile -t booking-local:test .
```

If wrapper is unavailable locally, use:

```bash
mvn clean verify
```

### GitHub Actions verification

1. Create a branch and push changes.
2. Open a PR to `main`.
3. Confirm `build-and-test` job runs and passes.
4. Merge (or push directly to `main` in allowed workflows).
5. Confirm `docker-build-and-push` job:
   - logs in successfully,
   - pushes `${{ github.sha }}` tag,
   - optionally pushes `latest` when enabled.

### Docker Hub verification

- Open your Docker Hub repository.
- Check the exact commit SHA tag exists for the run commit.
- If latest is enabled, check `latest` points to current `main` build.

## Placeholders / unknowns to finalize

1. **Docker repository name** currently defaults to:
   - `${DOCKERHUB_USERNAME}/moment-forever-booking`
   - Update in workflow if canonical repo name differs.
2. **Security scanner choice** is pending:
   - Replace placeholder with agreed tool (CodeQL/Snyk/Trivy/etc.).
3. **Latest-tag policy** is currently controlled by `PUSH_LATEST_ON_MAIN`:
   - Decide whether your release policy allows maintaining `latest`.
