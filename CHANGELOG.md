# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Sign in with GitHub by entering a short code on github.com; there is no token to create or paste
- Your GitHub profile in the drawer header and on its own screen
- Your GitHub star lists as a read-only filter in the drawer
- Star and unstar from a repository's screen, by swiping a card on Home (with Undo), and from search, which now also shows results from GitHub
- Pull-to-refresh removes repositories you no longer star on GitHub, after a one-time notice with the count

### Changed
- The app asks GitHub only for permission to read and change your stars, so private starred repositories no longer appear and are removed after that notice
- Every screen draws behind the system bars
- Updating from 1.1: sign in with GitHub once. If the app cannot tell whose starred repositories, tags and presets are on the phone, it asks whether to keep or remove them
- The personal access token from 1.1 is deleted from the phone but stays valid on GitHub; revoke it at github.com/settings/tokens
- Signing out deletes the key on the phone only; to remove Stargazer's access, revoke it under GitHub Settings → Applications → Authorized GitHub Apps

### Removed
- The token entry screen and the "update GitHub token" button

### Security
- The GitHub access key is encrypted on the device, left out of backups and never logged
- Signing settings are no longer kept in the repository

## [1.1] - 2026-02-01

### Added
- GitHub button on detail screen to open repository in browser
- Markdown image loading support via Coil3 integration
- Syntax highlighting for code blocks in README content
- GitHub icon drawable resource

### Changed
- Enhanced markdown renderer with `multiplatform-markdown-renderer-coil3` for images
- Enhanced markdown renderer with `multiplatform-markdown-renderer-code` for syntax highlighting
- Updated detail screen stats row layout to include GitHub button inline

## [1.0] - Initial Release

### Added
- GitHub starred repositories browser
- Sign-in with a GitHub personal access token
- Repository list with infinite scroll (Paging 3)
- Repository detail screen with README rendering
- Filter drawer with language and date filters
- Sort options (stars, forks, updated date)
- Search functionality
- Shared element transitions for Material Motion
- Custom Stargazer logo and launcher icon
- Full sync with starred_at ordering
- Manual release workflow for APK/AAB builds
