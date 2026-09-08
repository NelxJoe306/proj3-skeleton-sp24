package web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import spark.Spark;

/**
 * Local 3C server: serves the static SPA from {@code web/} and exposes one replay
 * endpoint. Boots Spark (bundled in {@code library-sp24/}) on an explicit port; the
 * default web root is {@code web} under the process CWD, overridable by argument.
 *
 * <p>No session state lives on the server (ADR-0004): every request carries its whole
 * Session descriptor and the engine derives the world as a pure function of it.
 */
public final class ServerMain {

    private static final int DEFAULT_PORT = 8077;
    private static final String DEFAULT_WEB_ROOT = "web";

    private ServerMain() {
    }

    /** Launches the server and parks the main thread until the process is killed. */
    public static void main(String[] args) throws InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        String webRoot = args.length > 1 ? args[1] : DEFAULT_WEB_ROOT;
        boot(port, Path.of(webRoot).toAbsolutePath().toString());
        System.out.println("proj3C server up at http://127.0.0.1:" + Spark.port());
        new CountDownLatch(1).await();
    }

    /** Registers routes and blocks until Spark is accepting connections. */
    public static void boot(int port, String webRoot) {
        Spark.port(port);
        Spark.staticFiles.externalLocation(webRoot);
        Spark.get("/", (req, res) -> {
            res.type("text/html; charset=utf-8");
            try {
                return Files.readString(Path.of(webRoot, "index.html"), StandardCharsets.UTF_8);
            } catch (IOException e) {
                res.status(500);
                return "index.html missing under " + webRoot;
            }
        });
        Spark.post("/api/replay", (req, res) -> {
            res.type("application/json; charset=utf-8");
            ReplayApi.Result r = ReplayApi.handle(req.body());
            res.status(r.status);
            return r.body;
        });
        Spark.init();
        Spark.awaitInitialization();
    }
}
