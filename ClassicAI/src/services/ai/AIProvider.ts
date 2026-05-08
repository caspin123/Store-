import { Message, ModelInfo } from '../../types';

export interface StreamCallback {
  onToken: (token: string) => void;
  onComplete: (fullText: string) => void;
  onError: (error: Error) => void;
}

export interface SendMessageOptions {
  messages: Message[];
  model: string;
  temperature?: number;
  maxTokens?: number;
  systemPrompt?: string;
  useThinking?: boolean;
  thinkingBudget?: number;
  stream?: boolean;
  streamCallback?: StreamCallback;
  signal?: AbortSignal;
}

export interface AIProvider {
  id: string;
  name: string;
  sendMessage(options: SendMessageOptions): Promise<string>;
  streamMessage(options: SendMessageOptions): Promise<void>;
  getModels(): Promise<ModelInfo[]>;
  supportsThinking(): boolean;
  supportsSearch(): boolean;
  validateApiKey(apiKey: string): Promise<boolean>;
}
