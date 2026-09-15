# joly0-patches

A personal patch bundle for YouTube, derived from
[MorpheApp/morphe-patches](https://github.com/MorpheApp/morphe-patches).

This is **not** an official Morphe project and is not affiliated with or endorsed by it. It is an
independent derivative published under the terms of the GPLv3, and it carries its own name
accordingly.

## What it adds

Everything the upstream bundle provides, plus one patch:

### Playlist bulk remove

Removing several videos from a playlist normally means swiping each row, choosing remove and
confirming, one video at a time. This draws a checkbox on every row of a playlist you own and
removes everything selected in a single request.

- Off by default. Enable **Feed → Select videos in playlists**, then reopen YouTube.
- Works on playlists you can edit, including Watch Later. Playlists you do not own cannot be
  edited by anyone, so no checkboxes appear there.
- A bar appears at the bottom once something is selected, with **All**, **Clear** and **Remove**.
- **Remove** asks for confirmation and lists the titles it resolved for your selection. That list
  is worth reading: it is what catches a mismatch before anything is deleted.
- Reordering the playlist while selecting is fine. Checkboxes follow their own video, including
  mid-drag.
- Long playlists load in pages as you scroll. Pressing **All** waits until the whole playlist has
  been read before selecting, so it never selects only the part that happens to be loaded.

## Using it with Morphe Manager

Add this repository as a patch source in Manager. It is a complete bundle, so it replaces the
official one rather than adding to it: you will be patching with this bundle's copy of every
patch, and you will not receive upstream patch updates until this repository is rebased onto
them.

## Supported YouTube version

Tracks upstream. Currently `21.13.164` is the newest non-experimental target.

## Building

```bash
./gradlew buildAndroid
```

Requires JDK 21, the Android SDK, and a GitHub token with `read:packages` in
`~/.gradle/gradle.properties` as `gpr.user` / `gpr.key`, because Morphe publishes the patcher and
its Gradle plugin to GitHub Packages.

## Licence and attribution

GPLv3, with the additional Section 7 terms in [NOTICE](NOTICE), inherited from upstream. The
upstream project's name, logos and trademarks are its own and are not used for the branding or
title of this derivative.

Upstream history is preserved in this repository's git history; the changes specific to this
bundle are the commits on top of it.
