# MyPrayers

A mobile app and home-screen widget for prayer-times tracking and notifications. The app ships as **«مواقيت» / Mawaqeet**; "MyPrayers" is only the repository's name.

## Public repository: no secrets

This repo is **public**. Never commit, or write into issues, PRs or comments:

- API keys, tokens, signing keys/keystores, provisioning profiles, `google-services.json` / `GoogleService-Info.plist` with real values, `.env` files
- Personal data: real names, emails, exact home coordinates, device IDs

Secrets go in local untracked files (see `.gitignore`) or the CI/hosting provider's secret store. Commit only `*.example` templates with placeholder values.

## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues on `ag2273/MyPrayers` (`gh` locally, GitHub MCP tools in cloud sessions). See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

The skills themselves are vendored in `.claude/skills/` from `mattpocock/skills` (pinned commit in `.claude/skills/.mattpocock-skills-version`).
