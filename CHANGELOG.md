# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
- OAuth authentication with GitHub
- Repository list with infinite scroll (Paging 3)
- Repository detail screen with README rendering
- Filter drawer with language and date filters
- Sort options (stars, forks, updated date)
- Search functionality
- Shared element transitions for Material Motion
- Custom Stargazer logo and launcher icon
- Full sync with starred_at ordering
- Manual release workflow for APK/AAB builds
