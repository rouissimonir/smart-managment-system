import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ConfirmService } from '../core/confirm.service';
import { TASK_STATUSES, Task, TaskStatus } from './task';
import { TaskService } from './task.service';

@Component({
  selector: 'app-task-list',
  imports: [RouterLink, FormsModule],
  templateUrl: './task-list.html',
})
export class TaskList {
  private readonly service = inject(TaskService);
  private readonly confirm = inject(ConfirmService);
  protected readonly auth = inject(AuthService);

  protected readonly statuses = TASK_STATUSES;
  protected readonly tasks = signal<Task[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected q = '';
  protected status: TaskStatus | '' = '';

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.list({ q: this.q || undefined, status: this.status || undefined, size: 100 }).subscribe({
      next: (page) => {
        this.tasks.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Tasks could not be loaded.');
      },
    });
  }

  remove(t: Task): void {
    void this.confirm.ask(`Delete task "${t.title}"?`).then((accepted) => {
      if (accepted) {
        this.service.delete(t.id).subscribe(() => this.load());
      }
    });
  }
}
