package com.cardio.lab;

import java.io.*;
import java.util.concurrent.*;

/** Ordered off-main-thread writes. Each batch is closed so partial recordings remain readable. */
final class SessionLog {
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    volatile String error;
    void append(File file, String text) {
        writer.execute(() -> {
            try (Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"))) {
                out.write(text);
            } catch (IOException e) { error = "Recording failed: " + e.getMessage(); }
        });
    }
    void copy(File source, OutputStream destination, Runnable success, java.util.function.Consumer<String> failure) {
        writer.execute(() -> {
            try (OutputStream out = destination; InputStream in = new FileInputStream(source)) {
                if (out == null) throw new IOException("Cannot open destination");
                byte[] buf = new byte[32768]; int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                success.run();
            } catch (IOException e) { failure.accept(e.getMessage()); }
        });
    }
    void close() { writer.shutdown(); }
}
