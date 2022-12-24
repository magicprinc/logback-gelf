/*
 * Logback GELF - zero dependencies Logback GELF appender library.
 * Copyright (C) 2016 Oliver Siegmar
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package de.siegmar.logbackgelf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.status.Status;

public class GelfTcpTlsAppenderTest {

    private static final String LOGGER_NAME = GelfTcpTlsAppenderTest.class.getCanonicalName();

    private int port;
    private Future<byte[]> future;

    public GelfTcpTlsAppenderTest() {
        final String mySrvKeystore =
            GelfTcpTlsAppenderTest.class.getResource("/mySrvKeystore").getFile();
        System.setProperty("javax.net.ssl.keyStore", mySrvKeystore);
        System.setProperty("javax.net.ssl.keyStorePassword", "secret");
    }

    @BeforeEach
    public void before() throws IOException {
        final TcpServer server = new TcpServer();
        port = server.getPort();
        future = Executors.newSingleThreadExecutor().submit(server);
    }

    @Test
    void defaultValues() {
        final GelfTcpTlsAppender appender = new GelfTcpTlsAppender();
        assertFalse(appender.isInsecure());
    }

    @Test
    public void configurationError() throws Exception {
        final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();

        final AtomicReference<Status> lastStatus = new AtomicReference<>();
        lc.getStatusManager().add(lastStatus::set);

        final GelfEncoder gelfEncoder = new GelfEncoder();
        gelfEncoder.setContext(lc);
        gelfEncoder.setOriginHost("localhost");
        gelfEncoder.start();

        final Logger logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
        logger.addAppender(buildAppender(lc, gelfEncoder));
        logger.setAdditive(false);

        final X509Certificate cert = new X509Util.CertBuilder()
            .build(null, "foo.example.com");

        final GelfTcpTlsAppender gelfAppender = new GelfTcpTlsAppender();
        gelfAppender.setContext(lc);
        gelfAppender.setName("GELF");
        gelfAppender.setEncoder(gelfEncoder);
        gelfAppender.setGraylogHost("localhost");
        gelfAppender.setGraylogPort(port);
        gelfAppender.setInsecure(true);
        gelfAppender.addTrustedServerCertificate(X509Util.toPEM(cert));
        gelfAppender.start();

        final IllegalStateException e = (IllegalStateException) lastStatus.get().getThrowable();
        assertEquals("Configuration options 'insecure' and 'trustedServerCertificates' "
            + "are mutually exclusive!", e.getMessage());
    }

    @Test
    public void simple() {
        final Logger logger = setupLogger();

        logger.error("Test message");

        stopLogger(logger);

        final JsonNode jsonNode = receiveMessage();
        assertEquals("1.1", jsonNode.get("version").textValue());
        assertEquals("localhost", jsonNode.get("host").textValue());
        assertEquals("Test message", jsonNode.get("short_message").textValue());
        assertTrue(jsonNode.get("timestamp").isNumber());
        assertEquals(3, jsonNode.get("level").intValue());
        assertNotNull(jsonNode.get("_thread_name").textValue());
        assertEquals(LOGGER_NAME, jsonNode.get("_logger_name").textValue());
    }

    private Logger setupLogger() {
        final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();

        final GelfEncoder gelfEncoder = new GelfEncoder();
        gelfEncoder.setContext(lc);
        gelfEncoder.setOriginHost("localhost");
        gelfEncoder.start();

        final Logger logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
        logger.addAppender(buildAppender(lc, gelfEncoder));
        logger.setAdditive(false);

        return logger;
    }

    private GelfTcpTlsAppender buildAppender(final LoggerContext lc, final GelfEncoder encoder) {
        final GelfTcpTlsAppender gelfAppender = new GelfTcpTlsAppender();
        gelfAppender.setContext(lc);
        gelfAppender.setName("GELF");
        gelfAppender.setEncoder(encoder);
        gelfAppender.setGraylogHost("localhost");
        gelfAppender.setGraylogPort(port);
        gelfAppender.setInsecure(true);
        gelfAppender.start();
        return gelfAppender;
    }

    private JsonNode receiveMessage() {
        try {
            return new ObjectMapper().readTree(receive());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void stopLogger(final Logger logger) {
        final GelfTcpTlsAppender gelfAppender = (GelfTcpTlsAppender) logger.getAppender("GELF");
        gelfAppender.stop();
    }

    private byte[] receive() {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class TcpServer implements Callable<byte[]> {

        private final SSLServerSocket server;

        TcpServer() throws IOException {
            final SSLServerSocketFactory sslserversocketfactory =
                (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            server = (SSLServerSocket) sslserversocketfactory.createServerSocket(0);
        }

        int getPort() {
            return server.getLocalPort();
        }

        @Override
        public byte[] call() throws Exception {
            final byte[] ret;

            try (Socket socket = server.accept()) {
                try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
                    ret = in.readAllBytes();
                }
            } finally {
                server.close();
            }

            return ret;
        }

    }

}
