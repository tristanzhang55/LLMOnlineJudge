package com.tfzhang.linuxback.grading.sandbox;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Drains process stdout and stderr concurrently so a full OS pipe on one
 * stream cannot deadlock {@code waitFor}.
 */
final class ProcessStreamDrainer {

    private ProcessStreamDrainer() {
    }

    static CompletableFuture<Captured> start(Process process) {
        CompletableFuture<byte[]> stdout = CompletableFuture.supplyAsync(
                () -> readAll(process.getInputStream()));
        CompletableFuture<byte[]> stderr = CompletableFuture.supplyAsync(
                () -> readAll(process.getErrorStream()));
        return stdout.thenCombine(stderr, Captured::new);
    }

    static Captured join(CompletableFuture<Captured> pending) {
        try {
            return pending.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while draining docker streams", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("failed to drain docker streams", e.getCause() == null ? e : e.getCause());
        } catch (CompletionException e) {
            throw new IllegalStateException("failed to drain docker streams", e.getCause() == null ? e : e.getCause());
        }
    }

    private static byte[] readAll(InputStream stream) {
        try {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    record Captured(byte[] stdout, byte[] stderr) {
    }
}
