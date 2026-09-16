package app.joly0.extension;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the headers needed to talk to InnerTube as the signed-in user.
 * <p>
 * The app builds every request through one place; a hook there hands the headers over. Nothing
 * here is ever logged: the authorization header is a live credential.
 * <p>
 * Each header is remembered separately rather than as one map, because no single request carries
 * all of them. The InnerTube calls to {@code youtubei.googleapis.com} send the authorization and
 * the visitor id but not {@code X-Goog-PageId}, which only appears on requests to
 * {@code www.youtube.com}. Replacing the whole map per request therefore threw the page id away
 * again the moment any InnerTube call followed, and without it a browse request resolves against
 * the primary account rather than the one on screen: a playlist of thousands came back holding a
 * single video.
 */
public final class AuthUtils {

    private static final String AUTHORIZATION = "Authorization";
    private static final String VISITOR_ID = "X-Goog-Visitor-Id";
    /** Selects the brand account. Without it a request acts on the primary account. */
    private static final String PAGE_ID = "X-Goog-PageId";

    private static volatile String authorization;
    private static volatile String visitorId;
    private static volatile String pageId;

    private AuthUtils() {
    }

    /** Injection point. Called for every request the app builds. */
    public static void setRequestHeaders(String url, Map<String, String> requestHeaders) {
        try {
            if (requestHeaders == null) {
                return;
            }
            // Each value is kept only when this request actually carries one, so a request
            // missing a header does not erase what an earlier one supplied.
            String newAuthorization = requestHeaders.get(AUTHORIZATION);
            if (isNotEmpty(newAuthorization)) {
                authorization = newAuthorization;
            }
            String newVisitorId = requestHeaders.get(VISITOR_ID);
            if (isNotEmpty(newVisitorId)) {
                visitorId = newVisitorId;
            }
            String newPageId = requestHeaders.get(PAGE_ID);
            if (isNotEmpty(newPageId)) {
                pageId = newPageId;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "setRequestHeaders failed", ex);
        }
    }

    private static boolean isNotEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    public static boolean isNotLoggedIn() {
        return !isNotEmpty(authorization);
    }

    /** Headers for an authenticated InnerTube call, empty until the app has made a request. */
    public static Map<String, String> getRequestHeaders() {
        if (isNotLoggedIn()) {
            return Collections.emptyMap();
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(AUTHORIZATION, authorization);
        if (isNotEmpty(visitorId)) {
            headers.put(VISITOR_ID, visitorId);
        }
        if (isNotEmpty(pageId)) {
            headers.put(PAGE_ID, pageId);
        }
        return Collections.unmodifiableMap(headers);
    }
}
