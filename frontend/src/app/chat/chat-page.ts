import { Component } from '@angular/core';

import { ChatPanel } from './chat-panel';

@Component({
  selector: 'app-chat-page',
  imports: [ChatPanel],
  template: `
    <div class="container">
      <div class="page-header"><h1>Assistant</h1></div>
      <app-chat-panel />
    </div>
  `,
})
export class ChatPage {}
