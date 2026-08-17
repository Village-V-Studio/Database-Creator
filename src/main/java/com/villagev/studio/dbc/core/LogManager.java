package com.villagev.studio.dbc.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.jline.reader.LineReader;

public class LogManager {
    private static final File LOG_DIR = new File("logs");
    private static final File LATEST_LOG = new File(LOG_DIR, "latest.log");

    private static LineReader activeLineReader;
    private static Thread mainThread;

    public static void setLineReader(LineReader reader) {
        activeLineReader = reader;
    }

    public static void init(boolean enableLogs) {
        mainThread = Thread.currentThread();

        if (!enableLogs) {
            return;
        }

        if (!LOG_DIR.exists()) {
            LOG_DIR.mkdirs();
        }

        rotateIfNewDay();

        try {
            boolean append = true;
            FileOutputStream fos = new FileOutputStream(LATEST_LOG, append);

            if (LATEST_LOG.exists() && LATEST_LOG.length() > 0) {
                String separator = "\n============================================================\n" +
                        "  NEW SESSION STARTED AT: "
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n" +
                        "============================================================\n";
                fos.write(separator.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            PrintStream consoleOut = System.out;
            PrintStream consoleErr = System.err;

            OutputStream dualOut = new DualOutputStream(consoleOut, fos);
            System.setOut(new PrintStream(dualOut, true, "UTF-8"));

            OutputStream dualErr = new DualOutputStream(consoleErr, fos);
            System.setErr(new PrintStream(dualErr, true, "UTF-8"));

        } catch (Exception e) {
            System.err.println("Failed to initialize file logger: " + e.getMessage());
        }
    }

    private static void rotateIfNewDay() {
        if (!LATEST_LOG.exists()) return;

        try {
            long lastMod = LATEST_LOG.lastModified();
            LocalDate lastModDate = java.time.Instant.ofEpochMilli(lastMod)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            LocalDate today = LocalDate.now();

            if (!lastModDate.equals(today)) {
                String dateStr = lastModDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                File archiveLog = new File(LOG_DIR, dateStr + ".log");

                int counter = 1;
                while (archiveLog.exists()) {
                    archiveLog = new File(LOG_DIR, dateStr + "_" + counter + ".log");
                    counter++;
                }

                Files.move(LATEST_LOG.toPath(), archiveLog.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("Failed to rotate logs: " + e.getMessage());
        }
    }

    private static class DualOutputStream extends OutputStream {
        private final OutputStream consoleOut;
        private final OutputStream fileOut;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final ThreadLocal<Boolean> bypassing = ThreadLocal.withInitial(() -> false);

        public DualOutputStream(OutputStream consoleOut, OutputStream fileOut) {
            this.consoleOut = consoleOut;
            this.fileOut = fileOut;
        }

        @Override
        public void write(int b) throws IOException {
            if (bypassing.get()) {
                consoleOut.write(b);
                return;
            }

            if (activeLineReader != null && activeLineReader.isReading() && Thread.currentThread() != mainThread) {
                if (b == '\n') {
                    flushLine();
                } else if (b != '\r') {
                    buffer.write(b);
                }
            } else {
                if (fileOut != null) fileOut.write(b);
                consoleOut.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (bypassing.get()) {
                consoleOut.write(b, off, len);
                return;
            }

            if (activeLineReader != null && activeLineReader.isReading() && Thread.currentThread() != mainThread) {
                for (int i = off; i < off + len; i++) {
                    write(b[i]);
                }
            } else {
                if (fileOut != null) fileOut.write(b, off, len);
                consoleOut.write(b, off, len);
            }
        }

        private synchronized void flushLine() throws IOException {
            String line = buffer.toString("UTF-8");
            buffer.reset();
            
            if (fileOut != null) {
                fileOut.write((line + "\n").getBytes("UTF-8"));
                fileOut.flush();
            }

            bypassing.set(true);
            try {
                activeLineReader.printAbove(line);
            } catch (Exception e) {
                consoleOut.write((line + "\n").getBytes("UTF-8"));
            } finally {
                bypassing.set(false);
            }
        }

        @Override
        public void flush() throws IOException {
            if (fileOut != null) fileOut.flush();
            consoleOut.flush();
        }

        @Override
        public void close() throws IOException {
            if (fileOut != null) fileOut.close();
            consoleOut.close();
        }
    }
}
