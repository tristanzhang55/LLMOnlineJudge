package com.tfzhang.linuxback.grading.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ProcessStreamDrainerTest {

    @Test
    @Timeout(5)
    void drain_readsBothStreamsWithoutWaitingForOneEofFirst() throws Exception {
        int size = 128 * 1024;
        PipedInputStream stdout = new PipedInputStream(1024);
        PipedInputStream stderr = new PipedInputStream(1024);
        PipedOutputStream stdoutSrc = new PipedOutputStream(stdout);
        PipedOutputStream stderrSrc = new PipedOutputStream(stderr);
        Process process = new StreamProcess(stdout, stderr);

        CompletableFuture<ProcessStreamDrainer.Captured> pending = ProcessStreamDrainer.start(process);
        Thread writerOut = new Thread(() -> writeAndClose(stdoutSrc, size));
        Thread writerErr = new Thread(() -> writeAndClose(stderrSrc, size));
        writerOut.start();
        writerErr.start();

        assertTimeoutPreemptively(java.time.Duration.ofSeconds(4), () -> {
            ProcessStreamDrainer.Captured captured = ProcessStreamDrainer.join(pending);
            assertEquals(size, captured.stdout().length);
            assertEquals(size, captured.stderr().length);
        });
        writerOut.join(1000);
        writerErr.join(1000);
    }

    @Test
    void drain_smallBuffers() {
        Process process = new StreamProcess(
                new ByteArrayInputStream("out".getBytes()),
                new ByteArrayInputStream("err".getBytes()));
        ProcessStreamDrainer.Captured captured = ProcessStreamDrainer.join(ProcessStreamDrainer.start(process));
        assertEquals("out", new String(captured.stdout()));
        assertEquals("err", new String(captured.stderr()));
    }

    private static void writeAndClose(PipedOutputStream stream, int size) {
        try {
            stream.write(new byte[size]);
            stream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class StreamProcess extends Process {
        private final InputStream stdout;
        private final InputStream stderr;

        private StreamProcess(InputStream stdout, InputStream stderr) {
            this.stdout = stdout;
            this.stderr = stderr;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
        }
    }
}
