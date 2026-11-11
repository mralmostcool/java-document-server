# AGENTS.md - Workspace Rules & Guardrails

## STRICT BOUNDARY GUARDRAIL: SCOPE OF WORK
- Never access anything outside the current working directory.
- Operate exclusively within project root: `c:\Users\Neeraj Gupta\Projects\mralmostcool\java-document-server`.
- Never create, modify, inspect, or delete files outside project root.
- Do not run commands targeting directories outside workspace boundary.
- Do not access parent directory `c:\Users\Neeraj Gupta\Projects\mralmostcool` or sibling directories.

## SAFETY & INTEGRITY GUARDRAILS
- Git safety: Never run `git reset --hard`, `git clean -fd`, or force-push (`git push -f`) without explicit user permission.
- Branch & Push Policy:
  - Use `dev` branch exclusively for all development work.
  - Never commit or push directly to `main` / `master`.
  - Always push development changes to the remote `dev` branch (`git push origin dev`).
- Credential safety: Do not commit secrets, API keys, passwords, or tokens. Use `.env` or system environment variables. Keep secrets out of logs.
- Sensitive files: Do not touch `.git` internals directly.
- Dependency safety: Verify and pin library dependencies. Avoid unverified third-party binaries or scripts.
- Port hygiene: Default development services (e.g., PostgreSQL, Redis, HTTP ports) must bind safely and avoid conflicting with host services.

## ARCHITECTURE & TECH STACK STANDARDS
- Language/Framework: Java (Spring Boot / Maven or Gradle standard layout).
- Clean separation of concerns: Controller, Service, Repository, DTO/Model layers.
- Production readiness: Comprehensive unit/integration tests, health endpoints, graceful error handling, structured logging.
- Docker: Keep `docker-compose.yml` clean, reproducible, with named volumes and explicit port mappings.

