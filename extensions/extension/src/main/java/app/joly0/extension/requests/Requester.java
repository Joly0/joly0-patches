/* Adapted from morphe-patches (GPLv3), app.morphe.extension.shared.requests. */

package app.joly0.extension.requests;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Requester {

    private Requester() {
    }

    public static HttpURLConnection getConnectionFromCompiledRoute(String apiUrl, Route.CompiledRoute route) throws IOException {
        String url = apiUrl + route.getCompiledRoute();
        HttpURLConnection connection = openConnection(url);
        // This request sends data via URL query parameters. No request body is included.
        // If a request body is added, the caller must set the appropriate Content-Length header.
        connection.setFixedLengthStreamingMode(0);
        connection.setRequestMethod(route.getMethod().name());
        // No default User-Agent is set here; the caller sets its own User-Agent.

        return connection;
    }

    public static HttpURLConnection openConnection(String url) throws IOException {
        return openConnection(new URL(url));
    }

    public static HttpURLConnection openConnection(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    /**
     * Parse the {@link HttpURLConnection}, and closes the underlying InputStream.
     */
    private static String parseInputStreamAndClose(InputStream inputStream) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        }
    }

    /**
     * Parse the {@link HttpURLConnection} response as a String.
     * This does not close the url connection.
     */
    public static String parseString(HttpURLConnection connection) throws IOException {
        return parseInputStreamAndClose(connection.getInputStream());
    }

    /**
     * Parse the {@link HttpURLConnection} response into a JSONObject.
     * This does not close the url connection.
     */
    public static JSONObject parseJSONObject(HttpURLConnection connection) throws JSONException, IOException {
        return new JSONObject(parseString(connection));
    }

}
