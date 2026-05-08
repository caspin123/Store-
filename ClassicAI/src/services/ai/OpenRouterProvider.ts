import { AIProvider, SendMessageOptions } from './AIProvider';
import { ModelInfo } from '../../types';
import { PROVIDER_MODELS } from '../../constants/providers';

export class OpenRouterProvider implements AIProvider {
  id = 'openrouter';
  name = 'OpenRouter';
  private apiKey: string;

  constructor(apiKey: string) {
    this.apiKey = apiKey;
  }

  async sendMessage(options: SendMessageOptions): Promise<string> {
    const { messages, model, temperature = 0.7, maxTokens = 2048, systemPrompt, signal } = options;
    const msgs = systemPrompt
      ? [{ role: 'system', content: systemPrompt }, ...messages.map(m => ({ role: m.role, content: m.content }))]
      : messages.map(m => ({ role: m.role, content: m.content }));

    const response = await fetch('https://openrouter.ai/api/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${this.apiKey}`,
        'Content-Type': 'application/json',
        'HTTP-Referer': 'https://classicai.app',
        'X-Title': 'Classic AI',
      },
      body: JSON.stringify({ model, messages: msgs, temperature, max_tokens: maxTokens }),
      signal,
    });

    if (!response.ok) {
      const err = await response.text();
      throw new Error(`OpenRouter error: ${response.status} - ${err}`);
    }

    const data = await response.json();
    return data.choices[0]?.message?.content || '';
  }

  async streamMessage(options: SendMessageOptions): Promise<void> {
    const { messages, model, temperature = 0.7, maxTokens = 2048, systemPrompt, streamCallback, signal } = options;
    const msgs = systemPrompt
      ? [{ role: 'system', content: systemPrompt }, ...messages.map(m => ({ role: m.role, content: m.content }))]
      : messages.map(m => ({ role: m.role, content: m.content }));

    const response = await fetch('https://openrouter.ai/api/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${this.apiKey}`,
        'Content-Type': 'application/json',
        'HTTP-Referer': 'https://classicai.app',
        'X-Title': 'Classic AI',
      },
      body: JSON.stringify({ model, messages: msgs, temperature, max_tokens: maxTokens, stream: true }),
      signal,
    });

    if (!response.ok) {
      const err = await response.text();
      throw new Error(`OpenRouter stream error: ${response.status} - ${err}`);
    }

    const reader = response.body?.getReader();
    if (!reader) throw new Error('No response body');

    const decoder = new TextDecoder();
    let fullText = '';
    let buffer = '';

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';
        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed.startsWith('data: ')) continue;
          const data = trimmed.slice(6);
          if (data === '[DONE]') continue;
          try {
            const parsed = JSON.parse(data);
            const token = parsed.choices?.[0]?.delta?.content || '';
            if (token) {
              fullText += token;
              streamCallback?.onToken(token);
            }
          } catch {
            // ignore parse errors for partial chunks
          }
        }
      }
    } finally {
      reader.releaseLock();
    }
    streamCallback?.onComplete(fullText);
  }

  async getModels(): Promise<ModelInfo[]> {
    return PROVIDER_MODELS.openrouter;
  }

  supportsThinking(): boolean { return false; }
  supportsSearch(): boolean { return false; }

  async validateApiKey(apiKey: string): Promise<boolean> {
    try {
      const res = await fetch('https://openrouter.ai/api/v1/models', {
        headers: { 'Authorization': `Bearer ${apiKey}` },
      });
      return res.ok;
    } catch {
      return false;
    }
  }
}
