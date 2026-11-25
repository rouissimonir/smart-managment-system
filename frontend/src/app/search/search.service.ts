import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { SearchMode, SearchResponse } from './search';

@Injectable({ providedIn: 'root' })
export class SearchService {
  private readonly http = inject(HttpClient);

  search(q: string, mode: SearchMode, projectId: string | null, page: number): Observable<SearchResponse> {
    let params = new HttpParams().set('q', q).set('mode', mode).set('page', page).set('size', 10);
    if (projectId) {
      params = params.set('projectId', projectId);
    }
    return this.http.get<SearchResponse>(`${environment.apiUrl}/search`, { params });
  }
}
