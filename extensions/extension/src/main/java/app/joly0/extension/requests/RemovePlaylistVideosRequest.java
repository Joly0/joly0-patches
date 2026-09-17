/* Adapted from morphe-patches (GPLv3), app.morphe.extension.youtube.patches.utils.requests. */

package app.joly0.extension.requests;

import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import app.joly0.extension.Logger;
import app.joly0.extension.Utils;

/**
 * Removes many videos from a playlist at once.
 * <p>
 * This is the bulk path: it sends one edit request per chunk of
 * {@link PlaylistRoutes#MAX_ACTIONS_PER_REQUEST} entries instead of one per video.
 */
public class RemovePlaylistVideosRequest {

    /**
     * Outcome of a bulk removal.
     *
     * @param removed The set video ids the server accepted.
     * @param failed  The set video ids that were not removed, either because a chunk failed
     *                outright or because the server reported a non success status for it.
     * @param reason  Why the last failure happened, short enough to show the user. Null when
     *                nothing failed.
     */
    public record Result(List<String> removed, List<String> failed, String reason) {

        public boolean isCompleteSuccess() {
            return failed.isEmpty();
        }

        public boolean isCompleteFailure() {
            return removed.isEmpty();
        }
    }

    private RemovePlaylistVideosRequest() {
    }

    /**
     * Blocking. Must be called off the main thread.
     *
     * @param setVideoIds Playlist entry ids, not video ids. Order is preserved, and the list is
     *                    chunked so that one rejected chunk does not lose the others.
     */
    public static Result remove(String playlistId, List<String> setVideoIds,
                                Map<String, String> requestHeader) {
        Utils.verifyOffMainThread();
        final long startTime = System.currentTimeMillis();

        List<String> removed = new ArrayList<>(setVideoIds.size());
        List<String> failed = new ArrayList<>();
        String reason = null;

        final int total = setVideoIds.size();
        for (int start = 0; start < total; start += PlaylistRoutes.MAX_ACTIONS_PER_REQUEST) {
            int end = Math.min(start + PlaylistRoutes.MAX_ACTIONS_PER_REQUEST, total);
            List<String> chunk = setVideoIds.subList(start, end);

            final String chunkFailure = sendChunk(playlistId, chunk, requestHeader);
            if (chunkFailure == null) {
                removed.addAll(chunk);
                continue;
            }

            if (chunk.size() == 1) {
                failed.addAll(chunk);
                reason = chunkFailure;
                continue;
            }

            // The server takes a chunk all or nothing, so one entry it will not accept took
            // every other entry in that chunk down with it and reported them all as failed.
            // Going back over them one at a time costs a request per entry, but only for the
            // chunk that had already failed, and it keeps the ones the server was happy with.
            Logger.printInfo(() -> "Retrying a failed chunk one entry at a time");
            for (String setVideoId : chunk) {
                final String failure = sendChunk(playlistId, List.of(setVideoId), requestHeader);
                if (failure == null) {
                    removed.add(setVideoId);
                } else {
                    failed.add(setVideoId);
                    reason = failure;
                }
            }
        }

        final int removedCount = removed.size();
        final int failedCount = failed.size();
        final String finalReason = reason;
        Logger.printDebug(() -> "Bulk removal of " + total + " entries: " + removedCount
                + " removed, " + failedCount + " failed, in "
                + (System.currentTimeMillis() - startTime) + "ms"
                + (finalReason == null ? "" : ", last failure: " + finalReason));

        return new Result(removed, failed, reason);
    }

    /**
     * Sends one edit request.
     *
     * @return null when the server accepted every entry, otherwise why it did not, phrased
     *         short enough to put in a toast.
     */
    private static String sendChunk(String playlistId, List<String> setVideoIds,
                                    Map<String, String> requestHeader) {
        try {
            byte[] requestBody = PlaylistRoutes.removeVideosBody(playlistId, setVideoIds);
            if (requestBody.length == 0) {
                return "empty request";
            }

            HttpURLConnection connection = PlaylistRoutes.getConnection(PlaylistRoutes.EDIT_PLAYLIST, requestHeader);
            connection.setFixedLengthStreamingMode(requestBody.length);
            connection.getOutputStream().write(requestBody);

            final int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                Logger.printInfo(() -> "Bulk remove failed with code: " + responseCode);
                // 401 here means the credentials the app handed over have gone stale, which is
                // worth telling apart from the playlist itself refusing the edit.
                return "HTTP " + responseCode;
            }

            JSONObject json = Requester.parseJSONObject(connection);
            final String status = json.optString("status");
            if ("STATUS_SUCCEEDED".equals(status)) {
                return null;
            }
            Logger.printInfo(() -> "Bulk remove returned status: " + status);
            return status.isEmpty() ? "no status returned" : status;
        } catch (SocketTimeoutException ex) {
            Logger.printInfo(() -> "Connection timeout", ex);
            return "timed out";
        } catch (IOException ex) {
            Logger.printInfo(() -> "Network error", ex);
            return "network error";
        } catch (Exception ex) {
            Logger.printException(() -> "sendChunk failed", ex);
            return "unexpected error";
        }
    }
}
