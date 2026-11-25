export type SearchMode = 'KEYWORD' | 'SEMANTIC' | 'HYBRID';

export interface SearchHit {
  chunkId: string;
  documentId: string;
  documentName: string;
  projectId: string | null;
  locator: string | null;
  snippet: string;
  score: number;
}

export interface SearchResponse {
  query: string;
  mode: SearchMode;
  embeddingModel: string;
  semanticMeaningful: boolean;
  page: number;
  size: number;
  hits: SearchHit[];
}
