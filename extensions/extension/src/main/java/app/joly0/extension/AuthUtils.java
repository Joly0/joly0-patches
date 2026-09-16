package app.joly0.extension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds the headers needed to talk to InnerTube as the signed-in user.
 * <p>
 * The app builds every request through one place; a hook there hands the headers over. Nothing
 * here is ever logged: the authorization header is a live credential.
 */
public final class AuthUtils {

    private static final String AUTHORIZATION = "Authorization";
    private static final String VISITOR_ID = "X-Goog-Visitor-Id";
    private static final String PAGE_ID = "X-Goog-PageId";

    private static volatile Map<String, String> headers = Collections.emptyMap();

    private AuthUtils() {
    }

    /** Injection point. Called for every request the app builds. */
    public static void setRequestHeaders(String url, Map<String, String> requestHeaders) {
        try {
            if (requestHeaders == null) {
                return;
            }
            String authorization = requestHeaders.get(AUTHORIZATION);
            if (authorization == null || authorization.isEmpty()) {
                return;
            }

            Map<String, String> captured = new HashMap<>();
            captured.put(AUTHORIZATION, authorization);
            String visitorId = requestHeaders.get(VISITOR_ID);
            if (visitorId != null) {
                captured.put(VISITOR_ID, visitorId);
            }
            // Selects the brand account. Without it a request acts on the primary account.
            String pageId = requestHeaders.get(PAGE_ID);
            if (pageId != null) {
                captured.put(PAGE_ID, pageId);
            }
            headers = Collections.unmodifiableMap(captured);
        } catch (Exception ex) {
            Logger.printException(() -> "setRequestHeaders failed", ex);
        }
    }

    public static boolean isNotLoggedIn() {
        return !headers.containsKey(AUTHORIZATION);
    }

    /** Headers for an authenticated InnerTube call, empty until the app has made a request. */
    public static Map<String, String> getRequestHeaders() {
        return headers;
    }
}
