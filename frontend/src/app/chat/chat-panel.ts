import { Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { DocumentService } from '../documents/document.service';
import { AppDocument } from '../documents/document';
import { ChatSource, ChatTurn } from './chat';
import { ChatService } from './chat.service';

@Component({
  selector: 'app-chat-panel',
  imports: [FormsModule],
  templateUrl: './chat-panel.html',
})
export class ChatPanel {
  readonly projectId = input<string | null>(null);

  private readonly chat = inject(ChatService);
  private readonly documents = inject(DocumentService);

  protected draft = '';
  protected readonly turns = signal<ChatTurn[]>([]);
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);
  private conversationId: string | null = null;

  send(): void {
    const message = this.draft.trim();
    if (!message || this.sending()) {
      return;
    }
    this.draft = '';
    this.error.set(null);
    this.turns.update((turns) => [...turns, { role: 'USER', content: message, sources: [] }]);
    this.sending.set(true);
    this.chat.ask(message, this.conversationId, this.projectId()).subscribe({
      next: (reply) => {
        this.conversationId = reply.conversationId;
        this.turns.update((turns) => [
          ...turns,
          { role: 'ASSISTANT', content: reply.answer, sources: reply.sources ?? [] },
        ]);
        this.sending.set(false);
      },
      error: (err) => {
        this.sending.set(false);
        this.error.set(err?.error?.detail ?? 'The assistant could not answer.');
      },
    });
  }

  openSource(source: ChatSource): void {
    const doc = { id: source.documentId, name: source.name } as AppDocument;
    this.documents.download(doc);
  }
}
