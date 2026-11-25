package com.smartmgmt.chat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmgmt.auth.UserPrincipal;
import com.smartmgmt.management.customer.Customer;
import com.smartmgmt.management.customer.CustomerRepository;
import com.smartmgmt.management.project.Project;
import com.smartmgmt.management.project.ProjectRepository;
import com.smartmgmt.management.project.ProjectStatus;
import com.smartmgmt.management.task.Task;
import com.smartmgmt.management.task.TaskRepository;
import com.smartmgmt.management.user.Role;

/**
 * Read-only lookups. The model never receives a SQL string. A USER only sees
 * projects they own and tasks assigned to them; a hidden project is reported
 * as not found so the answer does not confirm that it exists.
 */
@Service
@Transactional(readOnly = true)
public class DataTools {

    private static final Pattern DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern QUOTED = Pattern.compile("[\"']([^\"']+)[\"']");
    private static final List<ProjectStatus> CLOSED = List.copyOf(EnumSet.of(ProjectStatus.COMPLETED, ProjectStatus.CANCELLED));

    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final CustomerRepository customers;

    public DataTools(ProjectRepository projects, TaskRepository tasks, CustomerRepository customers) {
        this.projects = projects;
        this.tasks = tasks;
        this.customers = customers;
    }

    public String answer(String message, UserPrincipal current) {
        String text = message.toLowerCase(Locale.ROOT);
        if (text.contains("not updated") || text.contains("not contacted")) {
            return customersSince(message);
        }
        if (text.contains("how many") && text.contains("overdue") || text.contains("overdue project")) {
            return overdue(current);
        }
        if (text.contains("task")) {
            return tasksFor(message, current);
        }
        return projectByName(message, current);
    }

    private String overdue(UserPrincipal current) {
        long count = current.getRole() == Role.USER
                ? projects.countOverdueForOwner(LocalDate.now(), CLOSED, current.getId())
                : projects.countOverdue(LocalDate.now(), CLOSED);
        String scope = current.getRole() == Role.USER ? " that you own" : "";
        return "There are " + count + " overdue project" + (count == 1 ? "" : "s") + scope + ".";
    }

    private String projectByName(String message, UserPrincipal current) {
        String name = extractName(message);
        if (name == null) {
            return "Which project? Use the project name, for example: Who owns Project Alpha?";
        }
        Optional<Project> project = visible(name, current);
        if (project.isEmpty()) {
            return "No project named \"" + name + "\" was found.";
        }
        Project found = project.get();
        String owner = found.getOwner() == null ? "nobody"
                : (found.getOwner().getFullName() == null ? found.getOwner().getEmail() : found.getOwner().getFullName());
        String due = found.getDueDate() == null ? "no due date" : "due " + found.getDueDate();
        return found.getName() + " is " + found.getStatus() + ", owned by " + owner + ", " + due + ".";
    }

    private String tasksFor(String message, UserPrincipal current) {
        String name = extractName(message);
        if (name == null) {
            return "Which project should I list tasks for?";
        }
        Optional<Project> project = visible(name, current);
        if (project.isEmpty()) {
            return "No project named \"" + name + "\" was found.";
        }
        List<Task> found = tasks.findByProjectIdOrderByDueDateAsc(project.get().getId());
        if (current.getRole() == Role.USER) {
            found = found.stream()
                    .filter(task -> task.getAssignee() != null && current.getId().equals(task.getAssignee().getId()))
                    .toList();
        }
        if (found.isEmpty()) {
            return "No tasks are visible on " + project.get().getName() + ".";
        }
        StringBuilder answer = new StringBuilder("Tasks on " + project.get().getName() + ":");
        for (Task task : found) {
            answer.append("\n- ").append(task.getTitle()).append(" (").append(task.getStatus()).append(")");
            if (task.getAssignee() != null && task.getAssignee().getFullName() != null) {
                answer.append(", ").append(task.getAssignee().getFullName());
            }
        }
        return answer.toString();
    }

    private String customersSince(String message) {
        Matcher matcher = DATE.matcher(message);
        if (!matcher.find()) {
            return "Which date? Ask again with a date like 2026-01-01.";
        }
        LocalDate date;
        try {
            date = LocalDate.parse(matcher.group(1));
        } catch (DateTimeParseException ex) {
            return "Which date? Ask again with a date like 2026-01-01.";
        }
        Instant cutoff = date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        List<Customer> stale = customers.findByUpdatedAtBeforeOrderByNameAsc(cutoff, PageRequest.of(0, 20));
        if (stale.isEmpty()) {
            return "No customers were last updated before " + date + ".";
        }
        StringBuilder answer = new StringBuilder("Customers not updated since " + date + ":");
        for (Customer customer : stale) {
            answer.append("\n- ").append(customer.getName());
        }
        return answer.toString();
    }

    private Optional<Project> visible(String name, UserPrincipal current) {
        Optional<Project> found = findVisible(name, current);
        if (found.isEmpty() && !name.toLowerCase(Locale.ROOT).startsWith("project ")) {
            found = findVisible("Project " + name, current);
        }
        return found;
    }

    private Optional<Project> findVisible(String name, UserPrincipal current) {
        return projects.findByNameIgnoreCase(name).stream()
                .filter(project -> current.getRole() != Role.USER
                        || (project.getOwner() != null && current.getId().equals(project.getOwner().getId())))
                .findFirst();
    }

    static String extractName(String message) {
        Matcher quoted = QUOTED.matcher(message);
        if (quoted.find()) {
            return clean(quoted.group(1));
        }
        String lower = message.toLowerCase(Locale.ROOT);
        for (String marker : List.of("project ", "named ", "called ", "owns ", "for ")) {
            int at = lower.indexOf(marker);
            if (at >= 0) {
                return clean(message.substring(at + marker.length()));
            }
        }
        return null;
    }

    private static String clean(String raw) {
        String value = raw.trim().replaceAll("[?.!]+$", "").trim();
        return value.isEmpty() ? null : value;
    }
}
