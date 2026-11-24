export interface ChatSource {
  documentId: string;
  name: string;
  locator: string | null;
  chunkId: string;
}

export interface ChatReply {
  conversationId: string;
  answer: string;
  sources: ChatSource[];
  model: string;
}

export interface ChatTurn {
  role: 'USER' | 'ASSISTANT';
  content: string;
  sources: ChatSource[];
}
