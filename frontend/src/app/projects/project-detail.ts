import { Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ChatPanel } from '../chat/chat-panel';
import { AuthService } from '../core/auth.service';
import { ConfirmService } from '../core/confirm.service';
import { AppDocument } from '../documents/document';
import { DocumentService } from '../documents/document.service';
import { TASK_STATUSES, Task } from '../tasks/task';
import { TaskService } from '../tasks/task.service';
import { UserSummary } from '../users/user';
import { UserService } from '../users/user.service';
import { Project } from './project';
import { ProjectService } from './project.service';

@Component({
  selector: 'app-project-detail',
  imports: [RouterLink, ReactiveFormsModule, ChatPanel],
  templateUrl: './project-detail.html',
})
export class ProjectDetail {
  private readonly fb = inject(FormBuilder);
  private readonly projectService = inject(ProjectService);
  private readonly taskService = inject(TaskService);
  private readonly documentService = inject(DocumentService);
  private readonly userService = inject(UserService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);
  private readonly confirm = inject(ConfirmService);

  private readonly id = this.route.snapshot.paramMap.get('id')!;
  private readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');

  protected readonly taskStatuses = TASK_STATUSES;
  protected readonly project = signal<Project | null>(null);
  protected readonly tasks = signal<Task[]>([]);
  protected readonly documents = signal<AppDocument[]>([]);
  protected readonly users = signal<UserSummary[]>([]);
  protected readonly uploading = signal(false);
  protected readonly uploadError = signal<string | null>(null);
  protected readonly loadError = signal<string | null>(null);
  private pollTimer: ReturnType<typeof setInterval> | null = null;

  protected readonly taskForm = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    status: ['TODO' as (typeof TASK_STATUSES)[number]],
    assigneeId: [''],
    dueDate: [''],
  });

  constructor() {
    this.destroyRef.onDestroy(() => this.stopPolling());
    this.projectService.get(this.id).subscribe({
      next: (p) => this.project.set(p),
      error: () => this.loadError.set('This project could not be loaded.'),
    });
    this.loadTasks();
    this.loadDocuments();
    if (this.auth.hasRole('ADMIN', 'MANAGER')) {
      this.userService.list().subscribe((users) => this.users.set(users));
    }
  }

  loadTasks(): void {
    this.taskService.list({ projectId: this.id, size: 100 }).subscribe((page) => this.tasks.set(page.content));
  }

  loadDocuments(): void {
    this.documentService.listForProject(this.id).subscribe((docs) => {
      this.documents.set(docs);
      const pending = docs.some((d) => d.status === 'UPLOADED' || d.status === 'PROCESSING');
      if (pending && this.pollTimer === null) {
        this.pollTimer = setInterval(() => this.loadDocuments(), 2000);
      } else if (!pending) {
        this.stopPolling();
      }
    });
  }

  private stopPolling(): void {
    if (this.pollTimer !== null) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  /** ADMIN/MANAGER can edit any task; a USER can only advance a task assigned to them. */
  canEditTask(t: Task): boolean {
    return this.auth.hasRole('ADMIN', 'MANAGER') || t.assigneeId === this.auth.user()?.id;
  }

  /** ADMIN/MANAGER can upload to any project; a USER only to a project they own. */
  canUploadDocument(): boolean {
    const p = this.project();
    return this.auth.hasRole('ADMIN', 'MANAGER') || (!!p && p.ownerId === this.auth.user()?.id);
  }

  addTask(): void {
    if (this.taskForm.invalid) {
      this.taskForm.markAllAsTouched();
      return;
    }
    const v = this.taskForm.getRawValue();
    this.taskService
      .create({
        title: v.title,
        status: v.status,
        assigneeId: v.assigneeId || null,
        dueDate: v.dueDate || null,
        projectId: this.id,
      })
      .subscribe(() => {
        this.taskForm.reset({ title: '', status: 'TODO', assigneeId: '', dueDate: '' });
        this.loadTasks();
      });
  }

  cycleStatus(t: Task): void {
    const order = TASK_STATUSES;
    const next = order[(order.indexOf(t.status) + 1) % order.length];
    this.taskService
      .update(t.id, { title: t.title, description: t.description, status: next, projectId: t.projectId, assigneeId: t.assigneeId, dueDate: t.dueDate })
      .subscribe(() => this.loadTasks());
  }

  removeTask(t: Task): void {
    void this.confirm.ask(`Delete task "${t.title}"?`).then((accepted) => {
      if (accepted) {
        this.taskService.delete(t.id).subscribe(() => this.loadTasks());
      }
    });
  }

  uploadDocument(): void {
    const input = this.fileInput()?.nativeElement;
    const file = input?.files?.[0];
    if (!file) return;

    this.uploading.set(true);
    this.uploadError.set(null);
    this.documentService.upload(file, this.id).subscribe({
      next: () => {
        this.uploading.set(false);
        if (input) input.value = '';
        this.loadDocuments();
      },
      error: (err) => {
        this.uploading.set(false);
        this.uploadError.set(err?.error?.detail ?? 'Upload failed.');
      },
    });
  }

  downloadDocument(doc: AppDocument): void {
    this.documentService.download(doc);
  }

  reprocessDocument(doc: AppDocument): void {
    this.documentService.reprocess(doc.id).subscribe(() => this.loadDocuments());
  }

  removeDocument(doc: AppDocument): void {
    void this.confirm.ask(`Delete document "${doc.name}"?`).then((accepted) => {
      if (accepted) {
        this.documentService.remove(doc.id).subscribe(() => this.loadDocuments());
      }
    });
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  deleteProject(): void {
    const p = this.project();
    if (!p) return;
    void this.confirm.ask(`Delete project "${p.name}" and its tasks?`).then((accepted) => {
      if (accepted) {
        this.projectService.delete(p.id).subscribe(() => this.router.navigate(['/projects']));
      }
    });
  }
}
