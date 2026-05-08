import { ProviderID, ModelInfo } from '../types';

export const PROVIDER_MODELS: Record<ProviderID, ModelInfo[]> = {
  openrouter: [
    { id: 'openai/gpt-4o', name: 'GPT-4o', contextLength: 128000 },
    { id: 'anthropic/claude-3-5-sonnet', name: 'Claude 3.5 Sonnet', supportsThinking: true, contextLength: 200000 },
    { id: 'google/gemini-pro-1.5', name: 'Gemini Pro 1.5', contextLength: 1000000 },
    { id: 'deepseek/deepseek-r1', name: 'DeepSeek R1', supportsThinking: true, contextLength: 65536 },
    { id: 'meta-llama/llama-3.1-70b-instruct', name: 'Llama 3.1 70B', contextLength: 131072 },
  ],
  claude: [
    { id: 'claude-opus-4-7-20251101', name: 'Claude Opus 4.7', supportsThinking: true, contextLength: 200000 },
    { id: 'claude-sonnet-4-5-20251001', name: 'Claude Sonnet 4.5', supportsThinking: true, contextLength: 200000 },
    { id: 'claude-haiku-4-5-20251001', name: 'Claude Haiku 4.5', contextLength: 200000 },
  ],
  deepseek: [
    { id: 'deepseek-chat', name: 'DeepSeek Chat', contextLength: 65536 },
    { id: 'deepseek-reasoner', name: 'DeepSeek Reasoner', supportsThinking: true, contextLength: 65536 },
  ],
  gemini: [
    { id: 'gemini-2.0-flash-exp', name: 'Gemini 2.0 Flash', contextLength: 1000000 },
    { id: 'gemini-1.5-pro-latest', name: 'Gemini 1.5 Pro', contextLength: 2000000 },
    { id: 'gemini-1.5-flash-latest', name: 'Gemini 1.5 Flash', contextLength: 1000000 },
  ],
  custom: [
    { id: 'custom-model', name: 'Custom Model', contextLength: 32000 },
  ],
};

export const PROVIDER_NAMES: Record<ProviderID, string> = {
  openrouter: 'OpenRouter',
  claude: 'Anthropic Claude',
  deepseek: 'DeepSeek',
  gemini: 'Google Gemini',
  custom: 'Custom Provider',
};

export const PROVIDER_BASE_URLS: Record<ProviderID, string> = {
  openrouter: 'https://openrouter.ai/api/v1',
  claude: 'https://api.anthropic.com/v1',
  deepseek: 'https://api.deepseek.com/v1',
  gemini: 'https://generativelanguage.googleapis.com/v1beta',
  custom: '',
};
