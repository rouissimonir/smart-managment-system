import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { DocumentService } from '../documents/document.service';
import { AppDocument } from '../documents/document';
import { SearchHit, SearchMode, SearchResponse } from './search';
import { SearchService } from './search.service';

@Component({
  selector: 'app-search-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './search-page.html',
})
export class SearchPage {
  private readonly searchService = inject(SearchService);
  private readonly documentService = inject(DocumentService);
  private readonly route = inject(ActivatedRoute);

  protected readonly modes: SearchMode[] = ['HYBRID', 'KEYWORD', 'SEMANTIC'];
  protected q = '';
  protected mode: SearchMode = 'HYBRID';
  protected readonly projectId = this.route.snapshot.queryParamMap.get('projectId');
  protected readonly result = signal<SearchResponse | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected page = 0;

  run(): void {
    const query = this.q.trim();
    if (!query) {
      this.error.set('Enter a query.');
      return;
    }
    this.page = 0;
    this.load();
  }

  next(): void {
    this.page += 1;
    this.load();
  }

  previous(): void {
    this.page = Math.max(0, this.page - 1);
    this.load();
  }

  download(hit: SearchHit): void {
    const doc = { id: hit.documentId, name: hit.documentName } as AppDocument;
    this.documentService.download(doc);
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.searchService.search(this.q.trim(), this.mode, this.projectId, this.page).subscribe({
      next: (response) => {
        this.result.set(response);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.detail ?? 'Search failed.');
      },
    });
  }
}
