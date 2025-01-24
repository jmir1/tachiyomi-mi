# Changelog

All notable changes to this project will be documented in this file.

The format is a modified version of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
- `Added` - for new features.
- `Changed ` - for changes in existing functionality.
- `Improved` - for enhancement or optimization in existing functionality.
- `Removed` - for now removed features.
- `Fixed` - for any bug fixes.
- `Other` - for technical stuff.

## [Unreleased]
### Added

- feat(external-players): add mpvKt ([@Secozzi](https://github.com/Secozzi)) ([#1674](https://github.com/aniyomiorg/aniyomi/pull/1674))
- feat(player): video filters ([@abdallahmehiz](https://github.com/abdallahmehiz)) ([#1698](https://github.com/aniyomiorg/aniyomi/pull/1698))
- feat(player): Add better auto sub select ([@Secozzi](https://github.com/Secozzi)) ([#1706](https://github.com/aniyomiorg/aniyomi/pull/1706))
- feat(downloader): Copy the file location when using ext downloader ([@quickdesh](https://github.com/quickdesh)) ([#1758](https://github.com/aniyomiorg/aniyomi/pull/1758))
- feat(player): Replace player with mpvKt ([@Secozzi](https://github.com/Secozzi)) ([#1834](https://github.com/aniyomiorg/aniyomi/pull/1834), [#1855](https://github.com/aniyomiorg/aniyomi/pull/1855), [#1859](https://github.com/aniyomiorg/aniyomi/pull/1859), [#1860](https://github.com/aniyomiorg/aniyomi/pull/1860))

### Improved

- feat(entry): show "Now" instead of "0 minutes ago" ([@Secozzi](https://github.com/Secozzi)) ([#1715](https://github.com/aniyomiorg/aniyomi/pull/1715))

### Fixed

- Fix enhanced tracking for jellyfin ([@Secozzi](https://github.com/Secozzi)) ([#1656](https://github.com/aniyomiorg/aniyomi/pull/1656), [#1658](https://github.com/aniyomiorg/aniyomi/pull/1658))
- fix(animescreen): Fix airing time not showing ([@Secozzi](https://github.com/Secozzi)) ([#1720](https://github.com/aniyomiorg/aniyomi/pull/1720))
- fix hidden categories getting reset after delete/reorder ([@cuong-tran](https://github.com/cuong-tran)) ([#1780](https://github.com/aniyomiorg/aniyomi/pull/1780))
- Fix episode progress not being saved and duplicate tracks ([@perokhe](https://github.com/perokhe)) ([#1784](https://github.com/aniyomiorg/aniyomi/pull/1784), [#1785](https://github.com/aniyomiorg/aniyomi/pull/1785))
- Fix history date header duplication ([@quickdesh](https://github.com/quickdesh)) ([#1817](https://github.com/aniyomiorg/aniyomi/pull/1817))

### Other

- Merge from mihon until 0.16.5 ([@Secozzi](https://github.com/Secozzi)) ([#1663](https://github.com/aniyomiorg/aniyomi/pull/1663))
  - Merge until latest mihon commits ([@Secozzi](https://github.com/Secozzi)) ([#1693](https://github.com/aniyomiorg/aniyomi/pull/1693))
  - Merge until latest mihon commits (v0.17.0) ([@Secozzi](https://github.com/Secozzi)) ([#1804](https://github.com/aniyomiorg/aniyomi/pull/1804))
  - Merge until latest mihon commits (TODO(merge)) ([@Secozzi](https://github.com/Secozzi)) ([#1863](https://github.com/aniyomiorg/aniyomi/pull/1863))

## [v0.16.4.3] - 2024-07-01
### Fixed

- Fix extensions disappearing due to errors with the ClassLoader ([@jmir1](https://github.com/jmir1)) ([`959f84a`](https://github.com/aniyomiorg/aniyomi/commit/959f84ab41859f90c458c076d83d363ae086e47f))

## [v0.16.4.2] - 2024-07-01
### Fixed

- Hotfix to eliminate all proguard issues causing errors and crashes ([@jmir1](https://github.com/jmir1)) ([`a8cd723`](https://github.com/aniyomiorg/aniyomi/commit/a8cd7233dfdf26c98ff86b1871a7ac5774379b5e), [`a7644c2`](https://github.com/aniyomiorg/aniyomi/commit/a7644c268153fc0b9f10c27202591f960c6f6384), [`5045fa1`](https://github.com/aniyomiorg/aniyomi/commit/5045fa18ce5a1faa2130f1a33609e43d8453f078))

## [v0.16.4.1] - 2024-07-01
### Fixed

- Hotfix release to address errors with extensions ([@jmir1](https://github.com/jmir1)) ([`98d2528`](https://github.com/aniyomiorg/aniyomi/commit/98d252866e17beba7d9a4d094797e23c05ead6c1))

## [v0.16.4.0] - 2024-07-01
### Fixed

- fix(pip): pip not broadcasting intent in A14+ ([@quickdesh](https://github.com/quickdesh)) ([#1603](https://github.com/aniyomiorg/aniyomi/pull/1603))
- fix: advanced player settings crash in android ≤ 10 ([@perokhe](https://github.com/perokhe)) ([#1627](https://github.com/aniyomiorg/aniyomi/pull/1627))

### Improved

- feat: hide the skip intro button if the skipped amount == 0 ([@abdallahmehiz](https://github.com/abdallahmehiz)) ([#1598](https://github.com/aniyomiorg/aniyomi/pull/1598))

### Other

- Merge from mihon until mihon 0.16.2 ([@Secozzi](https://github.com/Secozzi)) ([#1578](https://github.com/aniyomiorg/aniyomi/pull/1578))
  - Merge from mihon until 0.16.4 ([@Secozzi](https://github.com/Secozzi)) ([#1601](https://github.com/aniyomiorg/aniyomi/pull/1601))