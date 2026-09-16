/* Adapted from morphe-patches (GPLv3), app.morphe.extension.youtube.patches.utils.requests. */

package app.joly0.extension.requests;

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
        String thumbnailUrl,
        int index
) {
}
