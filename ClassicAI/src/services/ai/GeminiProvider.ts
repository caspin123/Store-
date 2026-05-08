import { AIProvider, SendMessageOptions } from './AIProvider';
import { ModelInfo } from '../../types';
import { PROVIDER_MODELS } from '../../constants/providers';

interface GeminiPart {
  text: string;
}

interface GeminiContent {
  role: 'user' | 'model';
  parts: GeminiPart[];
}

export class GeminiProvider implements AIProvider {
  id = 'gemini';
  name = 'Google Gemini';
  private apiKey: string;
  private baseUrl = 'https://generativelanguage.googleapis.com/v1beta';

  constructor(apiKey: string) {
    this.apiKey = apiKey;
  }

  private mapMessages(messages: SendMessageOptions['messages'], systemPrompt?: string): {
    contents: GeminiContent[];
    systemInstruction?: { parts: GeminiPart[] };
  } {
    const contents: GeminiContent[] = messages
      .filter(m => m.role !== 'system')
      .map(m => ({
        role: m.role === 'assistant' ? 'model' : 'user',
        parts: [{ text: m.content }],
      }));

    const systemInstruction = systemPrompt
      ? { parts: [{ text: systemPrompt }] }
      : undefined;

    return { contents, systemInstruction };
  }

  async sendMessage(options: SendMessageOptions): Promise<string> {
    const { messages, model, temperature = 0.7, maxTokens = 2048, systemPrompt, signal } = options;
    const { contents, systemInstruction } = this.mapMessages(messages, systemPrompt);

    const url = `${this.baseUrl}/models/${model}:generateContent?key=${this.apiKey}`;

    const body: Record<string, unknown> = {
      contents,
      generationConfig: {
        temperature,
        maxOutputTokens: maxTokens,
      },
    };
    if (systemInstruction) body.systemInstruction = systemInstruction;

    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal,
    });

    if (!response.ok) {
      const err = await response.text();
      throw new Error(`Gemini error: ${response.status} - ${err}`);
    }

    const data = await response.json();
    return data.candidates?.[0]?.content?.parts?.[0]?.text || '';
  }

  async streamMessage(options: SendMessageOptions): Promise<void> {
    const { messages, model, temperature = 0.7, maxTokens = 2048, systemPrompt, streamCallback, signal } = options;
    const { contents, systemInstruction } = this.mapMessages(messages, systemPrompt);

    const url = `${this.baseUrl}/models/${model}:streamGenerateContent?alt=sse&key=${this.apiKey}`;

    const body: Record<string, unknown> = {
      contents,
      generationConfig: {
        temperature,
        maxOutputTokens: maxTokens,
      },
    };
    if (systemInstruction) body.systemInstruction = systemInstruction;

    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal,
    });

    if (!response.ok) {
      const err = await response.text();
      throw new Error(`Gemini stream error: ${response.status} - ${err}`);
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
            const token: string = parsed.candidates?.[0]?.content?.parts?.[0]?.text || '';
            if (token) {
              fullText += token;
              streamCallback?.onToken(token);
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
    return PROVIDER_MODELS.gemini;
  }

  supportsThinking(): boolean { return false; }
  supportsSearch(): boolean { return false; }

  async validateApiKey(apiKey: string): Promise<boolean> {
    try {
      const res = await fetch(
        `${this.baseUrl}/models?key=${apiKey}`,
      );
      return res.ok;
    } catch {
      return false;
    }
  }
}
