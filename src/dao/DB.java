package dao;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central DB connector.
 *
 * Reads RESOURCES/config.properties and supports switching between:
 *  - local  (localhost MySQL)
 *  - cloud  (Azure MySQL)
 *
 * In config.properties set:
 *   db.profile=local   OR   db.profile=cloud
 *
 * Then fill keys like:
 *   local.db.host, local.db.port, local.db.name, local.db.user, local.db.pass, ...
 *   cloud.db.host, cloud.db.port, cloud.db.name, cloud.db.user, cloud.db.pass, ...
 *
 * PERFORMANCE NOTE (important for Azure):
 * This class provides lightweight connection pooling without adding external jars.
 * It reuses connections instead of re-connecting on every click/refresh.
 */
public final class DB {

    private static final String CONFIG_RESOURCE = "/config.properties";
    private static Properties props;
    private static String jdbcUrl;
    private static String user;
    private static String pass;

    // ---------- simple pool (no external deps) ----------
    private static final Object POOL_LOCK = new Object();
    private static ArrayDeque<Connection> pool;
    private static int poolMax;
    private static int poolCreated;
    private static long borrowWaitMs;

    // If true, DB is already configured/initialized
    private static final AtomicBoolean inited = new AtomicBoolean(false);

    private DB() {}

    /**
     * Get a pooled connection.
     *
     * Callers MUST still use try-with-resources. Closing the connection returns it to the pool.
     */
    public static Connection getConnection() throws SQLException {
        ensureLoaded();
        return borrow();
    }

    /** Optional: quick test in console/logs. */
    public static void testConnection() {
        try (Connection c = getConnection()) {
            System.out.println("[DB] Connected OK: " + c.getMetaData().getURL());
        } catch (Exception e) {
            System.err.println("[DB] Connection FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ---------------- internal helpers ----------------

    private static synchronized void ensureLoaded() {
        if (inited.get()) return;

        props = new Properties();
        try (InputStream in = DB.class.getResourceAsStream(CONFIG_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Missing " + CONFIG_RESOURCE + ". Create it in RESOURCES/config.properties so it is on the classpath."
                );
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + CONFIG_RESOURCE + ": " + e.getMessage(), e);
        }

        // profile switch: local / cloud
        String profile = opt("db.profile", "local").trim().toLowerCase();
        if (!profile.equals("local") && !profile.equals("cloud")) {
            throw new IllegalStateException("db.profile must be 'local' or 'cloud' but was: " + profile);
        }

        // read using prefix: local.db.* or cloud.db.*
        String prefix = profile + ".db.";

        String host = req(prefix + "host");
        String port = opt(prefix + "port", "3306");
        String name = req(prefix + "name");

        user = req(prefix + "user");
        pass = req(prefix + "pass");

        // options (safe defaults)
        String useSSL = opt(prefix + "useSSL", profile.equals("cloud") ? "true" : "false");
        String sslMode = opt(prefix + "sslMode", profile.equals("cloud") ? "REQUIRED" : "DISABLED");
        String serverTz = opt(prefix + "serverTimezone", "Asia/Manila");
        String allowPK = opt(prefix + "allowPublicKeyRetrieval", profile.equals("cloud") ? "false" : "true");
        String useUnicode = opt(prefix + "useUnicode", "true");
        String enc = opt(prefix + "characterEncoding", "utf8");

        // performance options (works fine for both local & cloud)
        String cachePrep = opt(prefix + "cachePrepStmts", "true");
        String prepCacheSize = opt(prefix + "prepStmtCacheSize", "250");
        String prepCacheLimit = opt(prefix + "prepStmtCacheSqlLimit", "2048");
        String useServerPrep = opt(prefix + "useServerPrepStmts", "true");
        String tcpKeepAlive = opt(prefix + "tcpKeepAlive", "true");
        String rewriteBatched = opt(prefix + "rewriteBatchedStatements", "true");

        // Build URL
        jdbcUrl =
                "jdbc:mysql://" + host + ":" + port + "/" + name
                        + "?useSSL=" + useSSL
                        + "&sslMode=" + sslMode
                        + "&serverTimezone=" + serverTz
                        + "&allowPublicKeyRetrieval=" + allowPK
                        + "&useUnicode=" + useUnicode
                        + "&characterEncoding=" + enc
                        + "&cachePrepStmts=" + cachePrep
                        + "&prepStmtCacheSize=" + prepCacheSize
                        + "&prepStmtCacheSqlLimit=" + prepCacheLimit
                        + "&useServerPrepStmts=" + useServerPrep
                        + "&tcpKeepAlive=" + tcpKeepAlive
                        + "&rewriteBatchedStatements=" + rewriteBatched;

        // Pool config (per-profile override supported)
        // e.g. cloud.db.poolMax=8, cloud.db.borrowWaitMs=4000
        poolMax = parseInt(opt(prefix + "poolMax", "8"), 8);
        borrowWaitMs = parseLong(opt(prefix + "borrowWaitMs", "5000"), 5000);
        pool = new ArrayDeque<>(poolMax);
        poolCreated = 0;

        System.out.println("[DB] profile=" + profile + " url=" + safeUrl(jdbcUrl) + " user=" + user + " poolMax=" + poolMax);
        inited.set(true);
    }

    private static Connection borrow() throws SQLException {
        long deadline = System.currentTimeMillis() + borrowWaitMs;

        while (true) {
            Connection raw = null;
            synchronized (POOL_LOCK) {
                // Return an existing idle connection
                while (!pool.isEmpty()) {
                    Connection c = pool.pollFirst();
                    if (isUsable(c)) {
                        raw = c;
                        break;
                    } else {
                        closeQuiet(c);
                        poolCreated = Math.max(0, poolCreated - 1);
                    }
                }

                // Create new connection if pool isn't full
                if (raw == null && poolCreated < poolMax) {
                    raw = createNewConnection();
                    poolCreated++;
                }

                if (raw != null) {
                    // Wrap so close() returns to pool
                    return wrap(raw);
                }

                // Pool is exhausted; wait a bit
                long now = System.currentTimeMillis();
                long remaining = deadline - now;
                if (remaining <= 0) {
                    throw new SQLException("DB pool exhausted. Increase *.db.poolMax or ensure connections are closed.");
                }
                try {
                    POOL_LOCK.wait(Math.min(remaining, 250));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrupted while waiting for DB connection", ie);
                }
            }
        }
    }

    private static Connection createNewConnection() throws SQLException {
        Connection con = DriverManager.getConnection(jdbcUrl, user, pass);

        // Make network issues fail fast (avoid long freezes)
        try {
            con.setNetworkTimeout(Runnable::run, (int) TimeUnit.SECONDS.toMillis(10));
        } catch (Exception ignore) {}

        // Ensure PH timezone for this session (Azure MySQL often defaults to UTC).
        try (Statement st = con.createStatement()) {
            st.execute("SET time_zone = '+08:00'");
        } catch (Exception ignore) {
            // If permissions don't allow it, app will still work with Java-side timestamps.
        }

        return con;
    }

    private static boolean isUsable(Connection c) {
        if (c == null) return false;
        try {
            if (c.isClosed()) return false;
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    private static void returnToPool(Connection c) {
        if (c == null) return;
        synchronized (POOL_LOCK) {
            if (!inited.get()) {
                closeQuiet(c);
                return;
            }
            if (pool.size() >= poolMax) {
                closeQuiet(c);
                poolCreated = Math.max(0, poolCreated - 1);
            } else {
                pool.offerFirst(c);
            }
            POOL_LOCK.notifyAll();
        }
    }

    private static Connection wrap(Connection raw) {
        Objects.requireNonNull(raw, "raw connection");
        InvocationHandler handler = new InvocationHandler() {
            private final AtomicBoolean closed = new AtomicBoolean(false);

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if ("close".equals(name)) {
                    if (closed.compareAndSet(false, true)) {
                        returnToPool(raw);
                    }
                    return null;
                }
                if ("isClosed".equals(name)) {
                    return closed.get() || raw.isClosed();
                }
                try {
                    return method.invoke(raw, args);
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    throw ite.getTargetException();
                }
            }
        };
        return (Connection) Proxy.newProxyInstance(
                DB.class.getClassLoader(),
                new Class[]{Connection.class},
                handler
        );
    }

    private static void closeQuiet(Connection c) {
        try {
            if (c != null) c.close();
        } catch (Exception ignore) {}
    }

    private static String req(String key) {
        String v = props.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalStateException("Missing required property: " + key + " in " + CONFIG_RESOURCE);
        }
        return v.trim();
    }

    private static String opt(String key, String def) {
        String v = props.getProperty(key);
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static long parseLong(String s, long def) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static String safeUrl(String url) {
        return url.replaceAll("(?i)(password=)[^&]+", "$1***");
    }
}
