/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.utils.requests;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.requests.Requester;

public class GetPlaylistItemsRequest {

    /**
     * Upper bound on continuation requests for one playlist. The Android client returns 20
     * entries per page, so this covers 10,000 videos. The cap exists so that a malformed response
     * handing back the same token forever cannot spin indefinitely.
     */
    private static final int MAX_PAGES = 500;

    /**
     * One page of a playlist: its entries, and the token for the next page if there is one.
     */
    private record Page(List<PlaylistItem> items, @Nullable String continuation) {
    }

    /**
     * Receives the entries gathered so far, after each page.
     * <p>
     * A long playlist takes many round trips, and waiting for all of them before anything can be
     * selected makes the feature look broken on exactly the playlists it is most useful for.
     */
    public interface PageListener {
        /**
         * @param complete true when the playlist has been read to the end.
         * @return whether to fetch the next page. Returning false stops early, which is how a
         *         caller avoids pulling a four thousand entry playlist that nobody asked for.
         */
        boolean onPage(List<PlaylistItem> itemsSoFar, boolean complete);
    }

    private GetPlaylistItemsRequest() {
    }

    /**
     * Video id to set video id, for the first page of the playlist only.
     * <p>
     * Kept for callers that only need to resolve a single known video, such as removing the
     * currently playing video from the queue.
     */
    @Nullable
    public static Map<String, String> fetch(String playlistId, Map<String, String> requestHeader) {
        Page page = fetchPage(playlistId, requestHeader, null);
        if (page == null || page.items().isEmpty()) {
            return null;
        }

        Map<String, String> result = new HashMap<>();
        for (PlaylistItem item : page.items()) {
            result.put(item.videoId(), item.setVideoId());
        }
        return result;
    }

    /**
     * Every entry of the playlist, in playlist order, following continuation tokens until the
     * playlist is exhausted or {@link #MAX_PAGES} is reached.
     *
     * @param listener notified with the accumulated entries after each page, on this thread.
     * @return the entries, or an empty list if the first page could not be read. Never null.
     */
    public static List<PlaylistItem> fetchAll(String playlistId, Map<String, String> requestHeader,
                                              @Nullable PageListener listener) {
        Utils.verifyOffMainThread();
        final long startTime = System.currentTimeMillis();

        List<PlaylistItem> all = new ArrayList<>();
        String continuation = null;
        int page = 0;
        boolean stoppedByCaller = false;

        do {
            Page fetched = fetchPage(playlistId, requestHeader, continuation);
            if (fetched == null) {
                final int soFar = all.size();
                Logger.printInfo(() -> "Playlist page after " + soFar + " items failed");
                // Keep what was already gathered. A partial list means the rows past that point
                // stay unselectable, which is visible and harmless; throwing it away would make
                // the whole playlist unselectable instead.
                break;
            }

            all.addAll(fetched.items());
            continuation = fetched.continuation();
            page++;

            if (listener != null && !listener.onPage(reindex(all), continuation == null)) {
                stoppedByCaller = true;
                break;
            }
        } while (continuation != null && page < MAX_PAGES);

        if (continuation != null && !stoppedByCaller) {
            Logger.printInfo(() -> "Stopped at the page limit, playlist may be truncated");
        }

        final int count = all.size();
        final int pages = page;
        Logger.printDebug(() -> "Fetched " + count + " playlist items over " + pages + " page(s) in "
                + (System.currentTimeMillis() - startTime) + "ms");
        return reindex(all);
    }

    /** Each page numbers its own entries from zero, so the combined list is renumbered. */
    private static List<PlaylistItem> reindex(List<PlaylistItem> items) {
        List<PlaylistItem> indexed = new ArrayList<>(items.size());
        for (int i = 0, length = items.size(); i < length; i++) {
            PlaylistItem item = items.get(i);
            indexed.add(new PlaylistItem(item.videoId(), item.setVideoId(), item.title(),
                    item.author(), item.thumbnailUrl(), i));
        }
        return Collections.unmodifiableList(indexed);
    }

    @Nullable
    private static Page fetchPage(String playlistId, Map<String, String> requestHeader,
                                  @Nullable String continuation) {
        Utils.verifyOffMainThread();
        Logger.printDebug(() -> continuation == null
                ? "Fetching playlist items for: " + playlistId
                : "Fetching playlist continuation for: " + playlistId);

        try {
            byte[] requestBody = continuation == null
                    ? PlaylistRoutes.browsePlaylistBody(playlistId)
                    : PlaylistRoutes.browseContinuationBody(continuation);

            HttpURLConnection connection = PlaylistRoutes.getConnection(PlaylistRoutes.BROWSE_PLAYLIST, requestHeader);
            connection.setFixedLengthStreamingMode(requestBody.length);
            connection.getOutputStream().write(requestBody);
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                JSONObject json = Requester.parseJSONObject(connection);
                return parseResponse(json, continuation != null);
            }
            Logger.printInfo(() -> "Browse playlist failed with code: " + responseCode);
        } catch (SocketTimeoutException ex) {
            Logger.printInfo(() -> "Connection timeout", ex);
        } catch (IOException ex) {
            Logger.printInfo(() -> "Network error", ex);
        } catch (Exception ex) {
            Logger.printException(() -> "fetch failed", ex);
        }
        return null;
    }

    /**
     * The renderer holding a playlist's entries on a first page.
     */
    @Nullable
    private static JSONObject findBrowseRenderer(JSONObject json) throws JSONException {
        if (!json.has("contents")) {
            Logger.printDebug(() -> "JSON contents are missing, cannot parse response");
            return null;
        }
        JSONObject contents = json.getJSONObject("contents");
        JSONObject columnRenderer = contents.optJSONObject("singleColumnBrowseResultsRenderer");
        if (columnRenderer == null) {
            columnRenderer = contents.optJSONObject("twoColumnBrowseResultsRenderer");
        }
        if (columnRenderer == null) {
            return null;
        }

        JSONArray sectionContents = columnRenderer
                .getJSONArray("tabs")
                .getJSONObject(0)
                .getJSONObject("tabRenderer")
                .getJSONObject("content")
                .getJSONObject("sectionListRenderer")
                .getJSONArray("contents");

        for (int i = 0, length = sectionContents.length(); i < length; i++) {
            JSONObject section = sectionContents.getJSONObject(i);
            if (section.has("playlistVideoListRenderer")) {
                return section.getJSONObject("playlistVideoListRenderer");
            }
            JSONObject itemSection = section.optJSONObject("itemSectionRenderer");
            if (itemSection != null) {
                JSONArray inner = itemSection.getJSONArray("contents");
                for (int j = 0, innerLength = inner.length(); j < innerLength; j++) {
                    JSONObject innerItem = inner.getJSONObject(j);
                    if (innerItem.has("playlistVideoListRenderer")) {
                        return innerItem.getJSONObject("playlistVideoListRenderer");
                    }
                }
            }
        }
        return null;
    }

    /**
     * The renderer holding a playlist's entries on a continuation page, in the older shape the
     * Android client responds with.
     */
    @Nullable
    private static JSONObject findLegacyContinuationRenderer(JSONObject json) {
        JSONObject continuationContents = json.optJSONObject("continuationContents");
        return continuationContents == null
                ? null
                : continuationContents.optJSONObject("playlistVideoListContinuation");
    }

    /**
     * Entries of a continuation page in the newer shape, where they arrive as an append action
     * rather than inside a renderer.
     */
    @Nullable
    private static JSONArray findAppendedItems(JSONObject json) {
        JSONArray actions = json.optJSONArray("onResponseReceivedActions");
        if (actions == null) {
            return null;
        }
        for (int i = 0, length = actions.length(); i < length; i++) {
            JSONObject action = actions.optJSONObject(i);
            if (action == null) {
                continue;
            }
            JSONObject append = action.optJSONObject("appendContinuationItemsAction");
            if (append != null && append.has("continuationItems")) {
                return append.optJSONArray("continuationItems");
            }
        }
        return null;
    }

    /**
     * Continuation token in the older shape, held on the renderer itself.
     * <p>
     * This is the one the Android client actually uses. Only looking for the newer
     * {@code continuationItemRenderer} meant the token was never found, every playlist stopped
     * after its first page, and every row past that point matched nothing.
     */
    @Nullable
    private static String parseLegacyContinuation(JSONObject listRenderer) {
        JSONArray continuations = listRenderer.optJSONArray("continuations");
        if (continuations == null || continuations.length() == 0) {
            return null;
        }
        JSONObject first = continuations.optJSONObject(0);
        if (first == null) {
            return null;
        }
        JSONObject data = first.optJSONObject("nextContinuationData");
        if (data == null) {
            data = first.optJSONObject("reloadContinuationData");
        }
        if (data == null) {
            return null;
        }
        String token = data.optString("continuation");
        return token.isEmpty() ? null : token;
    }

    /** Continuation token in the newer shape, held on an entry within the contents array. */
    @Nullable
    private static String parseItemContinuation(JSONObject continuationItem) {
        JSONObject endpoint = continuationItem.optJSONObject("continuationEndpoint");
        if (endpoint == null) {
            return null;
        }
        JSONObject command = endpoint.optJSONObject("continuationCommand");
        if (command == null) {
            return null;
        }
        String token = command.optString("token");
        return token.isEmpty() ? null : token;
    }

    @Nullable
    private static Page parseResponse(JSONObject json, boolean isContinuation) {
        try {
            JSONObject listRenderer = isContinuation
                    ? findLegacyContinuationRenderer(json)
                    : findBrowseRenderer(json);

            JSONArray playlistContents;
            String continuation = null;

            if (listRenderer != null) {
                playlistContents = listRenderer.optJSONArray("contents");
                continuation = parseLegacyContinuation(listRenderer);
            } else {
                // Newer shape: a continuation page whose entries arrive as an append action.
                playlistContents = findAppendedItems(json);
            }

            if (playlistContents == null) {
                return null;
            }

            List<PlaylistItem> items = new ArrayList<>(playlistContents.length());
            for (int i = 0, length = playlistContents.length(); i < length; i++) {
                JSONObject element = playlistContents.optJSONObject(i);
                if (element == null) {
                    continue;
                }

                JSONObject continuationItem = element.optJSONObject("continuationItemRenderer");
                if (continuationItem != null) {
                    if (continuation == null) {
                        continuation = parseItemContinuation(continuationItem);
                    }
                    continue;
                }

                JSONObject renderer = element.optJSONObject("playlistVideoRenderer");
                if (renderer == null) {
                    continue;
                }

                String videoId = renderer.optString("videoId");
                String setVideoId = renderer.optString("setVideoId");
                if (videoId.isEmpty() || setVideoId.isEmpty()) {
                    continue;
                }

                items.add(new PlaylistItem(
                        videoId,
                        setVideoId,
                        parseText(renderer.optJSONObject("title")),
                        parseText(renderer.optJSONObject("shortBylineText")),
                        parseLargestThumbnail(renderer.optJSONObject("thumbnail")),
                        items.size()
                ));
            }

            return new Page(items, continuation);
        } catch (JSONException e) {
            Logger.printException(() -> "parseResponse failed", e);
        }
        return null;
    }

    /**
     * Text nodes arrive either as a plain simpleText or as a list of runs that must be joined.
     */
    private static String parseText(@Nullable JSONObject text) {
        if (text == null) {
            return "";
        }

        String simpleText = text.optString("simpleText");
        if (!simpleText.isEmpty()) {
            return simpleText;
        }

        JSONArray runs = text.optJSONArray("runs");
        if (runs == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0, length = runs.length(); i < length; i++) {
            JSONObject run = runs.optJSONObject(i);
            if (run != null) {
                builder.append(run.optString("text"));
            }
        }
        return builder.toString();
    }

    @Nullable
    private static String parseLargestThumbnail(@Nullable JSONObject thumbnail) {
        if (thumbnail == null) {
            return null;
        }
        JSONArray thumbnails = thumbnail.optJSONArray("thumbnails");
        if (thumbnails == null || thumbnails.length() == 0) {
            return null;
        }
        // The server orders these smallest first.
        JSONObject largest = thumbnails.optJSONObject(thumbnails.length() - 1);
        if (largest == null) {
            return null;
        }
        String url = largest.optString("url");
        return url.isEmpty() ? null : url;
    }
}
