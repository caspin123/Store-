import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import { Chat, Space, AISettings, ApiKeyConfig, ProviderID } from '../types';

const KEYS = {
  CHATS: 'chats',
  SPACES: 'spaces',
  SETTINGS: 'settings',
};

const apiKeyStorageKey = (provider: ProviderID) => `apikey_${provider}`;

export class StorageService {
  // ── Chats ────────────────────────────────────────────────────────────────

  static async loadChats(): Promise<Chat[]> {
    try {
      const raw = await AsyncStorage.getItem(KEYS.CHATS);
      if (!raw) return [];
      return JSON.parse(raw) as Chat[];
    } catch {
      return [];
    }
  }

  static async saveChats(chats: Chat[]): Promise<void> {
    try {
      await AsyncStorage.setItem(KEYS.CHATS, JSON.stringify(chats));
    } catch {}
  }

  // ── Spaces ───────────────────────────────────────────────────────────────

  static async loadSpaces(): Promise<Space[]> {
    try {
      const raw = await AsyncStorage.getItem(KEYS.SPACES);
      if (!raw) return [];
      return JSON.parse(raw) as Space[];
    } catch {
      return [];
    }
  }

  static async saveSpaces(spaces: Space[]): Promise<void> {
    try {
      await AsyncStorage.setItem(KEYS.SPACES, JSON.stringify(spaces));
    } catch {}
  }

  // ── Settings ─────────────────────────────────────────────────────────────

  static async loadSettings(): Promise<AISettings | null> {
    try {
      const raw = await AsyncStorage.getItem(KEYS.SETTINGS);
      if (!raw) return null;
      return JSON.parse(raw) as AISettings;
    } catch {
      return null;
    }
  }

  static async saveSettings(settings: AISettings): Promise<void> {
    try {
      await AsyncStorage.setItem(KEYS.SETTINGS, JSON.stringify(settings));
    } catch {}
  }

  // ── API Keys (SecureStore) ────────────────────────────────────────────────

  static async loadApiKey(provider: ProviderID): Promise<ApiKeyConfig | null> {
    try {
      const raw = await SecureStore.getItemAsync(apiKeyStorageKey(provider));
      if (!raw) return null;
      return JSON.parse(raw) as ApiKeyConfig;
    } catch {
      return null;
    }
  }

  static async saveApiKey(config: ApiKeyConfig): Promise<void> {
    try {
      await SecureStore.setItemAsync(
        apiKeyStorageKey(config.provider),
        JSON.stringify(config),
      );
    } catch {}
  }

  static async deleteApiKey(provider: ProviderID): Promise<void> {
    try {
      await SecureStore.deleteItemAsync(apiKeyStorageKey(provider));
    } catch {}
  }

  static async loadAllApiKeys(): Promise<Partial<Record<ProviderID, ApiKeyConfig | null>>> {
    const providers: ProviderID[] = ['openrouter', 'claude', 'deepseek', 'gemini', 'custom'];
    const results: Partial<Record<ProviderID, ApiKeyConfig | null>> = {};
    await Promise.all(
      providers.map(async p => {
        results[p] = await StorageService.loadApiKey(p);
      }),
    );
    return results;
  }
}
