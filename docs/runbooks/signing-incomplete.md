# Runbook: signing-incomplete

## When this fires

CI logs matching any of the following patterns trigger this runbook:

- `SIGNING_(STORE_PASSWORD|KEY_ALIAS|KEY_PASSWORD)`
- `signing.*(missing|not set)`

## Steps

1. Read the configuration error: it names the missing signing setting.
2. Set that one secret with `gh secret set <NAME>` (the person enters the value).
3. Re-run the failed release run: `gh run rerun <run-id>`.

## Notes

_Seeded from ship plan `recovery-playbooks[signing-incomplete]`. Update this file as the playbook evolves._
_Last synced from plan version: 1_
