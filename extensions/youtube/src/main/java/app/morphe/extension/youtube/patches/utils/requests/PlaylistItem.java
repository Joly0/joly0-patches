/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.utils.requests;

import androidx.annotation.Nullable;

/**
 * One entry of a playlist, as returned by the browse endpoint.
 *
 * @param videoId     The video. Not unique within a playlist: the same video can be added twice.
 * @param setVideoId  Identifies this particular entry. This, not the video id, is what a
 *                    remove action takes.
 * @param title       Video title, empty if the response did not carry one.
 * @param author      Channel name, empty if the response did not carry one.
 * @param thumbnailUrl Largest thumbnail offered, null if the response did not carry one.
 * @param index       Zero based position in the playlist, in the order the server returned.
 */
public record PlaylistItem(
        String videoId,
        String setVideoId,
        String title,
        String author,
        @Nullable String thumbnailUrl,
        int index
) {
}
