package com.villagev.studio.dbc.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class BinaryManagerTest {

    @Test
    void testOsAndArchDetection() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            assertTrue(BinaryManager.isWindows());
            assertFalse(BinaryManager.isLinux());
            assertFalse(BinaryManager.isMac());
        } else if (os.contains("mac")) {
            assertTrue(BinaryManager.isMac());
            assertFalse(BinaryManager.isWindows());
        } else if (os.contains("linux")) {
            assertTrue(BinaryManager.isLinux());
            assertFalse(BinaryManager.isWindows());
            assertFalse(BinaryManager.isMac());
        }

        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.matches("^(x86_64|amd64|x64)$")) {
            assertTrue(BinaryManager.isX86_64());
            assertFalse(BinaryManager.isArm64());
        } else if (arch.matches("^(aarch64|arm64|armv8.*)$")) {
            assertTrue(BinaryManager.isArm64());
            assertFalse(BinaryManager.isX86_64());
        }
    }

    @Test
    void testValidateBinariesWithEmptyDir(@TempDir Path tempDir) {
        File emptyDir = tempDir.toFile();
        assertFalse(BinaryManager.validateBinaries(emptyDir));
        assertFalse(BinaryManager.validateBinaries(null));
        assertFalse(BinaryManager.validateBinaries(new File(tempDir.toFile(), "non_existent")));
    }

    @Test
    void testDatabasesSafetyCheck(@TempDir Path tempDir) throws IOException {
        File databasesDir = tempDir.resolve("databases").toFile();
        databasesDir.mkdirs();
        File testDbFile = new File(databasesDir, "data.db");
        Files.writeString(testDbFile.toPath(), "vital user data");

        BinaryManager.cleanBinariesDir(databasesDir);

        assertTrue(testDbFile.exists(), "databases directory and its contents must NEVER be deleted!");
        assertEquals("vital user data", Files.readString(testDbFile.toPath()));
    }

    @Test
    void testCleanBinariesDirRemovesOnlyBinaries(@TempDir Path tempDir) throws IOException {
        File binariesDir = tempDir.resolve("mariaDB_binaries").toFile();
        File binDir = new File(binariesDir, "bin");
        binDir.mkdirs();
        File dummyBinary = new File(binDir, "dummy_broken_exe");
        Files.writeString(dummyBinary.toPath(), "broken");

        BinaryManager.cleanBinariesDir(binariesDir);

        assertTrue(binariesDir.exists());
        assertFalse(dummyBinary.exists());
    }

    @Test
    void testCanExecuteInDirectory(@TempDir Path tempDir) {
        boolean canExec = BinaryManager.canExecuteInDirectory(tempDir.toFile());
        assertTrue(canExec);
    }

    @Test
    void testResolveBinariesDir(@TempDir Path tempDir) {
        File preferredDir = tempDir.resolve("mariaDB_binaries").toFile();
        File resolved = BinaryManager.resolveBinariesDir(preferredDir);
        assertNotNull(resolved);
        assertTrue(BinaryManager.canExecuteInDirectory(resolved));
    }
}

