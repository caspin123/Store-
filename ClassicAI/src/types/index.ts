export type MessageRole = 'user' | 'assistant' | 'system';

export type ThinkingMode = 'direct' | 'extended';
export type SearchMode = 'standard' | 'extended';
export type ProviderID = 'openrouter' | 'claude' | 'deepseek' | 'gemini' | 'custom';

export interface Message {
  id: string;
  role: MessageRole;
  content: string;
  createdAt: number;
  status?: 'sending' | 'done' | 'error';
  metadata?: Record<string, any>;
}

export interface Chat {
  id: string;
  title: string;
  messages: Message[];
  createdAt: number;
  updatedAt: number;
  spaceId?: string;
}

export interface SpaceFile {
  id: string;
  name: string;
  content: string;
  type: 'pdf' | 'txt' | 'docx' | 'md';
  size: number;
  addedAt: number;
}

export interface SpaceLink {
  id: string;
  url: string;
  title?: string;
  addedAt: number;
}

export interface SpaceSettings {
  searchEnabled: boolean;
  searchMode: SearchMode;
  thinkingEnabled: boolean;
  thinkingMode: ThinkingMode;
  model?: string;
  provider?: ProviderID;
}

export interface Space {
  id: string;
  name: string;
  instructions: string;
  files: SpaceFile[];
  links: SpaceLink[];
  settings: SpaceSettings;
  chats: Chat[];
  createdAt: number;
  updatedAt: number;
}

export interface AISettings {
  provider: ProviderID;
  model: string;
  temperature: number;
  maxTokens: number;
  thinkingEnabled: boolean;
  thinkingMode: ThinkingMode;
  searchEnabled: boolean;
  searchMode: SearchMode;
}

export interface ProviderConfig {
  id: ProviderID;
  name: string;
  baseUrl?: string;
  apiKey?: string;
  models: ModelInfo[];
}

export interface ModelInfo {
  id: string;
  name: string;
  supportsThinking?: boolean;
  supportsSearch?: boolean;
  contextLength?: number;
}

export interface ApiKeyConfig {
  provider: ProviderID;
  apiKey: string;
  baseUrl?: string;
  customModelName?: string;
  testedAt?: number;
  isValid?: boolean;
}

export type ThinkingStatus =
  | 'idle'
  | 'thinking'
  | 'understanding'
  | 'searching'
  | 'searching_deep'
  | 'reviewing'
  | 'preparing'
  | 'generating';

export interface AppState {
  chats: Chat[];
  spaces: Space[];
  currentChatId: string | null;
  currentSpaceId: string | null;
  settings: AISettings;
  apiKeys: Record<ProviderID, ApiKeyConfig | null>;
  thinkingStatus: ThinkingStatus;
  isGenerating: boolean;
}
