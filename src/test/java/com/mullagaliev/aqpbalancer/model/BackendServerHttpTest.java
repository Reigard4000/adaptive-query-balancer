package com.mullagaliev.aqpbalancer.model;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendServerHttpTest {

    private static final QueryRequest REQUEST = new QueryRequest(
            "q-1",
            "SELECT avg(value_count) FROM analytics.events",
            1_000,
            QueryType.AGGREGATE,
            0.4,
            0L
    );

    @Test
    void parsesResultFromBackendResponse() throws Exception {
        try (StubHttpServer stub = new StubHttpServer(200, """
                {"status":"OK","result":42.5,"sampleSize":100,"totalRows":1000,"approximate":true}
                """)) {
            BackendServer server = httpServer(stub.url());

            ServerExecutionResult result = server.execute(REQUEST, Phase.UNKNOWN, StrategyType.STRATIFIED);

            assertTrue(result.success());
            assertEquals(200, result.statusCode());
            assertTrue(result.hasResult());
            assertEquals(42.5, result.result(), 1.0e-9);
            assertEquals(100, result.sampleSize());
            assertEquals(1_000L, result.totalRows());
            assertTrue(result.approximate());
        }
    }

    @Test
    void parsesErrorMessageFromFailedResponse() throws Exception {
        try (StubHttpServer stub = new StubHttpServer(503, """
                {"status":"ERROR","error":"Смоделированный сбой узла"}
                """)) {
            BackendServer server = httpServer(stub.url());

            ServerExecutionResult result = server.execute(REQUEST, Phase.UNKNOWN, StrategyType.EXACT);

            assertFalse(result.success());
            assertEquals(503, result.statusCode());
            assertEquals("Смоделированный сбой узла", result.error());
            assertFalse(result.hasResult());
        }
    }

    @Test
    void rejectsSuccessfulResponseWithoutResult() throws Exception {
        try (StubHttpServer stub = new StubHttpServer(200, "{\"status\":\"OK\"}")) {
            BackendServer server = httpServer(stub.url());

            ServerExecutionResult result = server.execute(REQUEST, Phase.UNKNOWN, StrategyType.UNIFORM);

            assertFalse(result.success());
            assertTrue(result.error().contains("не вернул результат"));
        }
    }

    private BackendServer httpServer(String url) {
        return new BackendServer("server-1", new ServerSettings(
                "stub",
                ServerType.HTTP,
                url,
                10,
                32,
                true,
                2_000,
                40.0,
                0.0
        ));
    }

    private static final class StubHttpServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final Thread worker;

        private StubHttpServer(int status, String body) throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.worker = new Thread(() -> serve(status, body), "stub-http-server");
            this.worker.setDaemon(true);
            this.worker.start();
        }

        private void serve(int status, String body) {
            try (Socket socket = serverSocket.accept()) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                );
                String line = reader.readLine();
                while (line != null && !line.isEmpty()) {
                    line = reader.readLine();
                }

                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                String reason = status == 200 ? "OK" : "Service Unavailable";
                String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                        + "Content-Type: application/json\r\n"
                        + "Content-Length: " + payload.length + "\r\n"
                        + "Connection: close\r\n\r\n";
                OutputStream output = socket.getOutputStream();
                output.write(headers.getBytes(StandardCharsets.UTF_8));
                output.write(payload);
                output.flush();
            } catch (IOException ignored) {
                // The test server can be closed before accepting a connection.
            }
        }

        private String url() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/execute";
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            worker.join(1_000L);
        }
    }
}
