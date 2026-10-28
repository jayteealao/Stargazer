# Runbook: signing-missing

## When this fires

CI logs matching any of the following patterns trigger this runbook:

- `-unsigned\.apk`
- `No APK found`
- `Found APK: *$`

## Steps

1. Confirm the four signing secrets exist: `gh secret list | grep -E '^SIGNING_(STORE_FILE_B64|STORE_PASSWORD|KEY_ALIAS|KEY_PASSWORD)\b'`.
2. Add each missing secret with `gh secret set <NAME>` (the person enters the value; never print or copy it). See `docs/release-signing.md`.
3. Delete the draft or broken release and its tag: `gh release delete v$VERSION --cleanup-tag --yes`.
4. Push the tag again: `git push origin v$VERSION`, then re-run `/wf ship <slug>` to resume at the workflow watch.

## Notes

_Seeded from ship plan `recovery-playbooks[signing-missing]`. Update this file as the playbook evolves._
_Last synced from plan version: 1_
