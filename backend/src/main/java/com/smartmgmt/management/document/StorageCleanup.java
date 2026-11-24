package com.smartmgmt.management.document;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Deletes stored files only after the surrounding transaction commits.
 * A rollback must not remove bytes that the database row still points at.
 */
public final class StorageCleanup {

    private StorageCleanup() {
    }

    public static void afterCommit(List<String> relativePaths, Consumer<String> deleter) {
        if (relativePaths.isEmpty()) {
            return;
        }
        List<String> paths = List.copyOf(relativePaths);
        Runnable task = () -> paths.forEach(deleter);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
