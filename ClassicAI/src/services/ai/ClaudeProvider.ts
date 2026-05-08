import { AIProvider, SendMessageOptions } from './AIProvider';
import { ModelInfo } from '../../types';
import { PROVIDER_MODELS } from '../../constants/providers';

export class ClaudeProvider implements AIProvider {
  id = 'claude';
  name = 'Anthropic Claude';
  private apiKey: string;

  constructor(apiKey: string) {
    this.apiKey = apiKey;
  }

  async sendMessage(options: SendMessageOptions): Promise<string> {
    const {
      messages,
      model,
      temperature = 0.7,
      maxTokens = 2048,
      systemPrompt,
      useThinking,
      thinkingBudget = 5000,
      signal,
    } = options;

    const msgs = messages
      .filter(m => m.role !== 'system')
      .map(m => ({ role: m.role as 'user' | 'assistant', content: m.content }));

    const body: Record<string, unknown> = {
      model,
      messages: msgs,
      max_tokens: maxTokens,
    };
    if (systemPrompt) body.system = systemPrompt;
    if (useThinking) {
      body.thinking = { type: 'enabled', budget_tokens: thinkingBudget };
    } else {
      body.temperature = temperature;
    }

    const response = await fetch('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: {
        'x-api-key': this.apiKey,
        'anthropic-version': '2023-06-01',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      signal,
    });

    if (!response.ok) {
      const err = await response.text();
      throw new Error(`Claude error: ${response.status} - ${err}`);
    }

    const data = await response.json();
    const textBlock = (data.content as Array<{ type: string; text?: string }>)?.find(b => b.type === 'text');
    return textBlock?.text || '';
  }

  async streamMessage(options: SendMessageOptions): Promise<void> {
    const {
      messages,
      model,
      temperature = 0.7,
      maxTokens = 2048,
      systemPrompt,
      useThinking,
      thinkingBudget = 5000,
      streamCallback,
      signal,
    } = options;

    const msgs = messages
      .filter(m => m.role !== 'system')
      .map(m => ({ role: m.role as 'user' | 'assistant', content: m.content }));

    const body: Record<string, unknown> = {
      model,
      messages: msgs,
      max_tokens: maxTokens,
      stream: true,
    };
    if (systemPrompt) body.system = systemPrompt;
    if (useThinking) {
      body.thinking = { type: 'enabled', budget_tokens: thinkingBudget };
    } else {
      body.temperature = temperature;
    }

    const response = await fetch('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: {
        'x-api-key': this.apiKey,
        'anthropic-version': '2023-06-01',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      signal,
    });

    if (!response.ok) {
      const err = await response.text();
      throw new Error(`Claude stream error: ${response.status} - ${err}`);
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
          try {
            const parsed = JSON.parse(data);
            if (
              parsed.type === 'content_block_delta' &&
              parsed.delta?.type === 'text_delta'
            ) {
              const token: string = parsed.delta.text || '';
              if (token) {
                fullText += token;
                streamCallback?.onToken(token);
              }
            }
          } catch {
            // ignore parse errors
          }
        }
      }
    } finally {
      reader.releaseLock();
    }
    streamCallback?.onComplete(fullText);
  }

  async getModels(): Promise<ModelInfo[]> {
    return PROVIDER_MODELS.claude;
  }

  supportsThinking(): boolean { return true; }
  supportsSearch(): boolean { return false; }

  async validateApiKey(apiKey: string): Promise<boolean> {
    try {
      const res = await fetch('https://api.anthropic.com/v1/models', {
        headers: {
          'x-api-key': apiKey,
          'anthropic-version': '2023-06-01',
        },
      });
      return res.ok;
    } catch {
      return false;
    }
  }
}
