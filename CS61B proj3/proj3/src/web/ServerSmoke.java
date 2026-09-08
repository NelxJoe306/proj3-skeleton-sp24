package web;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import spark.Spark;

/**
 * One-command process-level smoke for ticket 04: boots the real server on an ephemeral
 * port, drives it over HTTP (GET /, static /index.html, a replay POST, a bad POST, a
 * load-with-no-save POST), asserts the envelope/legend, then stops. Exits non-zero on
 * any failure. Run with the repo root as CWD (so {@code web/} and {@code save.txt} resolve).
 */
public final class ServerSmoke {

    private static int failures;

    public static void main(String[] args) throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        String webRoot = Path.of("web").toAbsolutePath().toString();
        new File("save.txt").delete(); // a clean slot for the load-with-no-save check

        ServerMain.boot(port, webRoot);
        try {
            checkGet("/", 200, "<!doctype html", "GET / serves the SPA HTML");
            checkGet("/index.html", 200, "<!doctype html", "static /index.html is reachable");
            checkPost("/api/replay",
                    "{\"kind\":\"new\",\"size\":\"medium\",\"seed\":42,\"keys\":\"DD\"}",
                    200, "\"legend\"", "a replay POST returns a snapshot envelope");
            checkPost("/api/replay",
                    "{\"kind\":\"new\",\"size\":\"huge\",\"seed\":1}",
                    400, "\"error\"", "an unknown size is a readable 400");
            checkPost("/api/replay",
                    "{\"kind\":\"load\"}",
                    200, "\"saveMissing\":true", "load with no save reports saveMissing");

            // Ticket 10 — the whole 3C path over HTTP: every tier answers with its own
            // dimensions, identical sessions replay to byte-identical bodies, and a
            // save-and-quit followed by load+more reproduces the uninterrupted session.
            String[][] tiers = {{"small", "40"}, {"medium", "80"}, {"large", "120"}};
            for (String[] tier : tiers) {
                checkPost("/api/replay",
                        "{\"kind\":\"new\",\"size\":\"" + tier[0] + "\",\"seed\":42,\"keys\":\"\"}",
                        200, "\"width\":" + tier[1],
                        "fresh " + tier[0] + " world reports width " + tier[1]);
            }

            String det = "{\"kind\":\"new\",\"size\":\"medium\",\"seed\":123,\"keys\":\"WASD\"}";
            check("determinism: identical sessions replay to identical bodies",
                    postBody("/api/replay", det).equals(postBody("/api/replay", det)),
                    "two identical POSTs disagreed");

            new File("save.txt").delete();
            int[] saveStatus = new int[1];
            http("POST", "/api/replay",
                    "{\"kind\":\"new\",\"size\":\"small\",\"seed\":7,\"keys\":\"W:Q\"}", saveStatus);
            check("save-and-quit over HTTP writes the save file",
                    saveStatus[0] == 200 && new File("save.txt").exists(),
                    "status " + saveStatus[0]);

            String loaded = postBody("/api/replay", "{\"kind\":\"load\",\"keys\":\"WAASSDD\"}");
            check("load+more resumes without saveMissing",
                    !loaded.contains("\"saveMissing\":true"),
                    loaded.contains("\"saveMissing\":true") ? "save reported missing" : "bad shape");

            String uninterrupted = postBody("/api/replay",
                    "{\"kind\":\"new\",\"size\":\"small\",\"seed\":7,\"keys\":\"WWAASSDD\"}");
            check("session continuation holds over the wire: load+more == uninterrupted",
                    loaded.equals(uninterrupted), "bodies differ");
            new File("save.txt").delete(); // leave the tree clean
            System.out.println(failures == 0 ? "SMOKE OK"
                    : "SMOKE FAILED (" + failures + " checks failed)");
        } finally {
            Spark.stop();
        }
        if (failures != 0) {
            System.exit(1);
        }
    }

    private static void checkGet(String path, int wantStatus, String mustContain, String label)
            throws IOException {
        int[] status = new int[1];
        String body = http("GET", path, null, status);
        check(label, status[0] == wantStatus && body.contains(mustContain),
                "got status " + status[0]);
    }

    private static void checkPost(String path, String json, int wantStatus, String mustContain,
                                  String label) throws IOException {
        int[] status = new int[1];
        String body = http("POST", path, json, status);
        check(label, status[0] == wantStatus && body.contains(mustContain),
                "got status " + status[0] + ": " + abbreviate(body));
    }

    /** POST and return the raw body, letting the caller assert on full-body equality. */
    private static String postBody(String path, String json) throws IOException {
        return http("POST", path, json, new int[1]);
    }

    private static void check(String label, boolean ok, String detail) {
        failures += ok ? 0 : 1;
        System.out.println((ok ? "PASS  " : "FAIL  ") + label + (ok ? "" : " — " + detail));
    }

    private static String http(String method, String path, String body, int[] outStatus)
            throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + Spark.port() + path).openConnection();
        c.setRequestMethod(method);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        outStatus[0] = c.getResponseCode();
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
        if (is != null) {
            is.close();
        }
        return text;
    }

    private static String abbreviate(String s) {
        return s.length() <= 160 ? s : s.substring(0, 160) + "…";
    }
}
