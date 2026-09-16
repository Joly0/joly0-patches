## [1.0.2](https://github.com/Joly0/joly0-patches/compare/v1.0.1...v1.0.2) (2026-09-16)

### Bug Fixes

* ship the dex entries Morphe Manager needs to load the bundle ([10755bf](https://github.com/Joly0/joly0-patches/commit/10755bf48cfc963c766f87983da505c5c0545363))
* generate the patch list before building for Android, so the dex is not overwritten ([10755bf](https://github.com/Joly0/joly0-patches/commit/10755bf48cfc963c766f87983da505c5c0545363))
* refuse to publish a bundle that contains no classes.dex ([10755bf](https://github.com/Joly0/joly0-patches/commit/10755bf48cfc963c766f87983da505c5c0545363))

## [1.0.1](https://github.com/Joly0/joly0-patches/compare/v1.0.0...v1.0.1) (2026-09-16)

### Bug Fixes

* declare the YouTube app signatures, without which a manager can match no installed app ([81e8ac3](https://github.com/Joly0/joly0-patches/commit/81e8ac37888102c8a6c2da201d8953e07e2bb7b0))
* stop publishing a patch list whose version disagreed with the release ([81e8ac3](https://github.com/Joly0/joly0-patches/commit/81e8ac37888102c8a6c2da201d8953e07e2bb7b0))
* turn debug logging off by default ([81e8ac3](https://github.com/Joly0/joly0-patches/commit/81e8ac37888102c8a6c2da201d8953e07e2bb7b0))

### Known Issues

* the bundle still contained no dex entries, so Morphe Manager could not load it and showed the source with zero patches. Fixed in 1.0.2.

## [1.0.0](https://github.com/Joly0/joly0-patches/compare/v1.43.1-joly0.2...v1.0.0) (2026-09-16)

### Features

* **Playlist bulk remove:** draw a checkbox on each row of a playlist you own and remove every selected video in one request ([5ebc5da](https://github.com/Joly0/joly0-patches/commit/5ebc5da45c6ae0632c56cb27a5c9d4c7ce1fa8e0))
* match rows to videos by title rather than position, so reordering a playlist while selecting does not move the ticks onto the wrong videos ([5ebc5da](https://github.com/Joly0/joly0-patches/commit/5ebc5da45c6ae0632c56cb27a5c9d4c7ce1fa8e0))
* confirm with the resolved video titles before anything is removed ([5ebc5da](https://github.com/Joly0/joly0-patches/commit/5ebc5da45c6ae0632c56cb27a5c9d4c7ce1fa8e0))
* read the current playlist id from the Litho page header component ([f052b83](https://github.com/Joly0/joly0-patches/commit/f052b8381b723588d3d1b7592f8516ee457c405b))
* page the playlist lazily, one page on open and the rest only on demand ([fd86c24](https://github.com/Joly0/joly0-patches/commit/fd86c24083c0e7010579d2eccbe4b9cf9d122355))

### Notes

* this bundle holds a single patch under its own namespace and is meant to be applied alongside the official bundle rather than instead of it. Earlier releases of this repository were a fork of the whole upstream bundle ([a2142af](https://github.com/Joly0/joly0-patches/commit/a2142afd28b02d6280d09fa6f734c7577674fb6b))

### Known Issues

* the compatibility descriptor declared no app signatures, and the bundle contained no dex entries, so Morphe Manager could not use it. Fixed in 1.0.1 and 1.0.2.
