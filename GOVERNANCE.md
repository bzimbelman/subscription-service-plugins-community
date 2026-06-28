# Governance

This document describes how decisions are made in this project and how the project is organized.

## Project scope

This repository is the **community-plugins catalog** for the bzonfhir subscription-service: JVM modules that implement the SPIs published by the engine and extend the pipeline at well-defined extension points. The engine lives at [bzimbelman/subscription-service](https://github.com/bzimbelman/subscription-service). The same governance model applies across all the project's public repos.

## Maintainership

Today the project has a **single maintainer**: Brian Zimbelman ([@bzimbelman](https://github.com/bzimbelman)). The maintainer is the final decision-maker on:

- What plugins are accepted into `main`.
- Whether a plugin needs an engine-side SPI change before it can land here.
- Release timing and per-plugin version numbers.
- The published roadmap.

Single-maintainer governance is intentional at this stage: plugins ship into customers' production deployments, and the bar for accepted code is deliberately high. A single point of accountability keeps that bar consistent. Responses may be slower than a multi-maintainer project — please be patient.

## Planned scale-up

When sustained external contribution arrives — concretely, when at least three external contributors have each landed three or more substantive PRs over a 90-day window — the maintainer will propose a **steering committee** to take over governance of this repo and the companions. The committee will be a small group (three to five members) chartered to:

- Resolve technical disputes the maintainer cannot resolve alone.
- Approve breaking changes to the published SPI (which are coordinated with the engine repo).
- Add and remove committee members.
- Update this document.

The steering-committee bylaws will be drafted at that time and published as a follow-up to this file. Until then, the maintainer makes those calls.

## Decision-making in the meantime

For day-to-day decisions:

- **Accepting a plugin**: the maintainer reviews, requests changes if needed, and merges when CI is green.
- **Rejecting a plugin**: the maintainer says no with a reason. Common reasons: out of scope, security concern, depends on engine internals instead of the SPI, missing tests, scope creep.
- **Disagreement**: open a GitHub Discussion on the [engine repo](https://github.com/bzimbelman/subscription-service/discussions). The maintainer will respond. If you still disagree, you are welcome to fork.

## Contribution sign-off (DCO, not CLA)

All commits MUST be signed off under the [Developer Certificate of Origin](https://developercertificate.org/):

```bash
git commit -s -m "your message"
```

The `-s` flag appends a `Signed-off-by:` trailer to the commit, which is your assertion that you have the right to contribute the code under the project's license (Apache 2.0).

We do **not** use a Contributor License Agreement. The DCO + Apache 2.0 combination keeps the contribution path simple: no separate paperwork, no clickwrap, no corporate signatory required.

DCO enforcement on pull requests is provided by the [DCO GitHub App](https://github.com/apps/dco). The app installation is an org-level setting; the maintainer is responsible for keeping it installed and configured for this repository. The per-repo config lives in `.github/dco.yml`.

## Code of conduct

All participation in this project is subject to the [Code of Conduct](CODE_OF_CONDUCT.md) (Contributor Covenant v2.1). The maintainer enforces it.

## Security

Security issues should be reported privately via the repository's Security Advisory page, or by opening a minimal public issue requesting a private contact channel. Do **not** file a public issue containing exploit detail. See `SECURITY.md` when present.

## Licensing

Code is Apache 2.0. Documentation is CC BY 4.0 unless a directory says otherwise. By submitting a contribution you agree it is licensed under the same terms as the surrounding file.

## Amending this document

Today: the maintainer amends this file by PR-and-merge. After the steering committee stands up: changes require a majority vote of the committee, recorded in the PR conversation.
