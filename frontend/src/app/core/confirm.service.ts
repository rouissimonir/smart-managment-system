import { Injectable, signal } from '@angular/core';

/**
 * One app-wide confirmation, so delete actions do not use window.confirm.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly message = signal<string | null>(null);
  private resolver: ((accepted: boolean) => void) | null = null;

  ask(message: string): Promise<boolean> {
    if (this.resolver) {
      this.resolver(false);
    }
    this.message.set(message);
    return new Promise((resolve) => {
      this.resolver = resolve;
    });
  }

  answer(accepted: boolean): void {
    const resolve = this.resolver;
    this.resolver = null;
    this.message.set(null);
    resolve?.(accepted);
  }
}
