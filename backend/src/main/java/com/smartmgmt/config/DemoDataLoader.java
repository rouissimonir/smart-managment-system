package com.smartmgmt.config;

import java.time.LocalDate;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.smartmgmt.management.customer.Customer;
import com.smartmgmt.management.customer.CustomerRepository;
import com.smartmgmt.management.document.Document;
import com.smartmgmt.management.document.DocumentRepository;
import com.smartmgmt.management.document.DocumentStatus;
import com.smartmgmt.management.document.DocumentStorageService;
import com.smartmgmt.management.document.ingest.IngestionQueue;
import com.smartmgmt.management.project.Project;
import com.smartmgmt.management.project.ProjectRepository;
import com.smartmgmt.management.project.ProjectStatus;
import com.smartmgmt.management.task.Task;
import com.smartmgmt.management.task.TaskRepository;
import com.smartmgmt.management.task.TaskStatus;
import com.smartmgmt.management.user.User;
import com.smartmgmt.management.user.UserRepository;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.event.ApplicationReadyEvent;

/**
 * Idempotent demo rows: two customers, two projects, one assigned task, and the
 * two policy files the retrieval eval expects. Each new file is queued for
 * ingestion after this transaction commits, so a fresh demo stack can search
 * them without a second restart.
 */
@Component
@Order(0)
@ConfigurationProperties(prefix = "app.demo")
public class DemoDataLoader {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final UserRepository users;
    private final CustomerRepository customers;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final DocumentRepository documents;
    private final DocumentStorageService storage;
    private final IngestionQueue ingestion;

    @Getter
    @Setter
    private boolean seed = false;

    public DemoDataLoader(UserRepository users, CustomerRepository customers, ProjectRepository projects,
            TaskRepository tasks, DocumentRepository documents, DocumentStorageService storage,
            IngestionQueue ingestion) {
        this.users = users;
        this.customers = customers;
        this.projects = projects;
        this.tasks = tasks;
        this.documents = documents;
        this.storage = storage;
        this.ingestion = ingestion;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (!seed) {
            return;
        }
        User manager = users.findByEmail("manager@local").orElse(null);
        User member = users.findByEmail("user@local").orElse(null);
        if (manager == null) {
            return;
        }

        Customer northwind = customer("Northwind", "ops@northwind.example", "Northwind Traders");
        customer("Contoso", "hello@contoso.example", "Contoso Ltd");

        Project website = project("Website revamp", "Publish the customer policies.",
                ProjectStatus.IN_PROGRESS, northwind, manager, LocalDate.of(2024, 1, 15));
        if (member != null) {
            project("Mobile rollout", "A project the USER account owns.",
                    ProjectStatus.PLANNING, northwind, member, LocalDate.of(2026, 12, 1));
            if (tasks.findByProjectIdOrderByDueDateAsc(website.getId()).isEmpty()) {
                Task task = new Task();
                task.setTitle("Confirm the refund window");
                task.setStatus(TaskStatus.TODO);
                task.setProject(website);
                task.setAssignee(member);
                task.setDueDate(LocalDate.of(2024, 2, 1));
                tasks.save(task);
            }
        }

        storePolicy(website, manager, "Refund Policy.md");
        storePolicy(website, manager, "Account Closure Terms.md");
        log.info("Demo seed checked");
    }

    private Customer customer(String name, String email, String company) {
        return customers.findFirstByNameIgnoreCase(name).orElseGet(() -> {
            Customer created = new Customer();
            created.setName(name);
            created.setEmail(email);
            created.setCompany(company);
            return customers.save(created);
        });
    }

    private Project project(String name, String description, ProjectStatus status, Customer customer,
            User owner, LocalDate dueDate) {
        return projects.findByNameIgnoreCase(name).stream().findFirst().orElseGet(() -> {
            Project created = new Project();
            created.setName(name);
            created.setDescription(description);
            created.setStatus(status);
            created.setCustomer(customer);
            created.setOwner(owner);
            created.setDueDate(dueDate);
            return projects.save(created);
        });
    }

    private void storePolicy(Project project, User uploader, String filename) {
        if (documents.existsByName(filename)) {
            return;
        }
        try {
            byte[] bytes = new ClassPathResource("demo/" + filename).getContentAsByteArray();
            Document document = new Document();
            document.setName(filename);
            document.setMimeType("text/markdown");
            document.setSizeBytes(bytes.length);
            document.setStoragePath(storage.storeBytes(bytes, "md"));
            document.setProject(project);
            document.setUploadedBy(uploader);
            document.setStatus(DocumentStatus.UPLOADED);
            Document saved = documents.save(document);
            enqueueAfterCommit(saved.getId());
        } catch (Exception e) {
            log.warn("Could not seed {}", filename, e);
        }
    }

    private void enqueueAfterCommit(UUID documentId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ingestion.submit(documentId);
                }
            });
        } else {
            ingestion.submit(documentId);
        }
    }
}
