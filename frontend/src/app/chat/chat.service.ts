import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { ChatReply } from './chat';

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);

  ask(message: string, conversationId: string | null, projectId: string | null): Observable<ChatReply> {
    return this.http.post<ChatReply>(`${environment.apiUrl}/chat`, {
      message,
      conversationId,
      projectId,
    });
  }
}
