# End-to-end flows

These Maestro flows drive the real app on an emulator. Run each one with:

```
maestro --device <emulator-serial> test -e OUT=<dir> maestro/<flow>.yaml
```

Screenshots land in `<dir>`. Run them on an emulator, not on a personal phone: several flows clear
or seed the app's data.

Some flows need a fixture first. The fixtures are instrumented seed classes, run with
`adb -s <emulator-serial> shell am instrument -w -e class <SeedClass> -e <extra> <value> uk.adedamola.stargazer.test/uk.adedamola.stargazer.HiltTestRunner`.

| Flow | Seed and device state | Needs a person |
|---|---|---|
| `sign-in.yaml` | Fresh install, online. Asks GitHub for one real code (50 per hour per app) | No |
| `sign-in-approve.yaml` | Online | Yes: approves the code on github.com |
| `access-ended.yaml` | `SignInFixtureSeed` with `-e fixtureKey <key GitHub rejects>`, online | No |
| `restore-sign-in.yaml` | `AccountRestoreCheck` with `-e restoreSeed true`, then a backup, uninstall and restore | No |
| `edge-to-edge.yaml` | `EdgeToEdgeFixtureSeed` with `-e seedFixture true`, airplane mode on | No |
| `profile-and-lists.yaml` | `ProfileFixtureSeed` with `-e profileFixture default`, airplane mode on | No |
| `star-offline.yaml` | `ProfileFixtureSeed` with `-e profileFixture default`, airplane mode on | No |
| `profile-and-lists-live.yaml` | A real signed-in account (after `sign-in-approve.yaml`) | Yes: the account owner |
| `charter-live.yaml` | A real signed-in account with at least one star list | Yes: the account owner |
| `charter-revoked.yaml` | A real signed-in account; the owner revokes the app on github.com first | Yes: revokes, then approves a new code |

The flows with no person in the loop can run unattended, one after another, in the order above.
The flows that need a person read the account only: they never star, unstar or type into GitHub.
