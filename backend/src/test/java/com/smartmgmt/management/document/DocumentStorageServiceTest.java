package com.smartmgmt.management.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentStorageServiceTest {

    @TempDir
    Path root;

    private DocumentStorageService storage;

    @BeforeEach
    void setUp() {
        DocumentStorageProperties properties = new DocumentStorageProperties();
        properties.setDocumentsDir(root.toString());
        storage = new DocumentStorageService(properties);
        storage.init();
    }

    @Test
    void deleteRemovesAFileInsideTheRoot() throws Exception {
        Path file = root.resolve("report.txt");
        Files.writeString(file, "hello");

        storage.delete("report.txt");

        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void deleteRefusesAPathThatEscapesTheRoot() throws Exception {
        Path outside = root.getParent().resolve("outside-secret.txt");
        Files.writeString(outside, "do not delete");

        assertThatThrownBy(() -> storage.delete("../outside-secret.txt"))
                .isInstanceOf(DocumentStorageException.class);

        assertThat(Files.readString(outside)).isEqualTo("do not delete");
        Files.deleteIfExists(outside);
    }
}
