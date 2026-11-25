import { Component, inject } from '@angular/core';

import { ConfirmService } from './confirm.service';

@Component({
  selector: 'app-confirm-dialog',
  template: `
    @if (confirm.message(); as message) {
      <div class="modal-backdrop" (click)="confirm.answer(false)">
        <div class="modal card" role="dialog" aria-modal="true" (click)="$event.stopPropagation()">
          <h2>Confirm</h2>
          <p>{{ message }}</p>
          <div class="row-actions">
            <button class="btn" type="button" (click)="confirm.answer(false)">Cancel</button>
            <button class="btn btn-danger" type="button" (click)="confirm.answer(true)">Delete</button>
          </div>
        </div>
      </div>
    }
  `,
})
export class ConfirmDialog {
  protected readonly confirm = inject(ConfirmService);
}
