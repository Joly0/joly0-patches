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
     */
    public record Result(List<String> removed, List<String> failed) {

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

        final int total = setVideoIds.size();
        for (int start = 0; start < total; start += PlaylistRoutes.MAX_ACTIONS_PER_REQUEST) {
            int end = Math.min(start + PlaylistRoutes.MAX_ACTIONS_PER_REQUEST, total);
            List<String> chunk = setVideoIds.subList(start, end);

            if (sendChunk(playlistId, chunk, requestHeader)) {
                removed.addAll(chunk);
            } else {
                failed.addAll(chunk);
            }
        }

        final int removedCount = removed.size();
        final int failedCount = failed.size();
        Logger.printDebug(() -> "Bulk removal of " + total + " entries: " + removedCount
                + " removed, " + failedCount + " failed, in "
                + (System.currentTimeMillis() - startTime) + "ms");

        return new Result(removed, failed);
    }

    private static boolean sendChunk(String playlistId, List<String> setVideoIds,
                                     Map<String, String> requestHeader) {
        try {
            byte[] requestBody = PlaylistRoutes.removeVideosBody(playlistId, setVideoIds);
            if (requestBody.length == 0) {
                return false;
            }

            HttpURLConnection connection = PlaylistRoutes.getConnection(PlaylistRoutes.EDIT_PLAYLIST, requestHeader);
            connection.setFixedLengthStreamingMode(requestBody.length);
            connection.getOutputStream().write(requestBody);

            final int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                Logger.printInfo(() -> "Bulk remove failed with code: " + responseCode);
                return false;
            }

            JSONObject json = Requester.parseJSONObject(connection);
            final String status = json.optString("status");
            if ("STATUS_SUCCEEDED".equals(status)) {
                return true;
            }
            Logger.printInfo(() -> "Bulk remove returned status: " + status);
        } catch (SocketTimeoutException ex) {
            Logger.printInfo(() -> "Connection timeout", ex);
        } catch (IOException ex) {
            Logger.printInfo(() -> "Network error", ex);
        } catch (Exception ex) {
            Logger.printException(() -> "sendChunk failed", ex);
        }
        return false;
    }
}
