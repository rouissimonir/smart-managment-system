import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { UserSummary } from './user';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);

  /** ADMIN/MANAGER only. Used to pick a project owner or a task assignee. */
  list(): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>(`${environment.apiUrl}/users`);
  }
}
