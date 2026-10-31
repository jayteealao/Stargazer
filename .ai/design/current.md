---
schema: sdlc/v1
type: design-current
updated-at: "2026-10-24T22:01:25Z"
design-system: "none"
---

## Surfaces

| Surface | Path | Last changed by (slug) | Newest board | Drift from the system |
|---|---|---|---|---|
| Login (token paste) | app/src/main/java/uk/adedamola/stargazer/ui/screens/LoginScreen.kt | — | none | Uses a plain Material Button and OutlinedTextField, not StargazerButton |
| Home (starred list) | app/src/main/java/uk/adedamola/stargazer/ui/screens/HomeScreen.kt | — | none | Raw "ERROR: HTTP 401" text on failure; one empty state for every case |
| Detail | app/src/main/java/uk/adedamola/stargazer/ui/screens/DetailScreen.kt | — | none | No actions except "open on GitHub" |
| Filter drawer | app/src/main/java/uk/adedamola/stargazer/ui/components/FilterDrawer.kt | — | none | Header text only; no account identity |
| Repo card | app/src/main/java/uk/adedamola/stargazer/ui/components/RepoCard.kt | — | none | Icons without content descriptions |
| Search bar | app/src/main/java/uk/adedamola/stargazer/ui/components/SearchBar.kt | — | none | — |
| Tag dialogs | app/src/main/java/uk/adedamola/stargazer/ui/components/TagDialogs.kt | — | none | Eight fixed tag colors |
| Primary button | app/src/main/java/uk/adedamola/stargazer/ui/components/StargazerButton.kt | — | none | Defined but unused |

## System in code

- Tokens: `ui/theme/Color.kt` (Factory palette and Material 3 roles), `ui/theme/Theme.kt` (always-dark scheme, status bar colored directly), `ui/theme/Type.kt` (sans-serif display and headlines, monospace titles, body and labels).
- Components: Material 3 Compose with Material icons; `StargazerButton` (chamfered orange button), `RepoCard`, `FilterDrawer`, `SearchBar`, `TagDialogs`; `FilterBar` unused.
- No edge-to-edge handling: the app never calls `enableEdgeToEdge`.

## Design debt

| Debt | Where | Created by | Paid by |
|---|---|---|---|
| Empty state does not tell "no stars", "no filter match" and "error" apart | HomeScreen.kt | before workflows | |
| Icons and color-only signals lack text alternatives | RepoCard.kt, FilterDrawer.kt | before workflows | |
| Splash background is dark while the launcher icon has a light ground | themes.xml, launcher icon | before workflows | |
| Single-column only; no foldable or tablet layout | all screens | before workflows | |
| No edge-to-edge insets | MainActivity.kt, all screens | before workflows | |
