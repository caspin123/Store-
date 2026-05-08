import React, {
  createContext,
  useContext,
  useReducer,
  useEffect,
  useRef,
  useCallback,
} from 'react';
import { v4 as uuidv4 } from 'uuid';
import {
  AppState,
  Chat,
  Message,
  Space,
  AISettings,
  ApiKeyConfig,
  ProviderID,
  ThinkingStatus,
} from '../types';
import { StorageService } from '../storage/StorageService';
import { SearchService } from '../services/SearchService';
import { OpenRouterProvider } from '../services/ai/OpenRouterProvider';
import { ClaudeProvider } from '../services/ai/ClaudeProvider';
import { DeepSeekProvider } from '../services/ai/DeepSeekProvider';
import { GeminiProvider } from '../services/ai/GeminiProvider';
import { CustomProvider } from '../services/ai/CustomProvider';
import type { AIProvider } from '../services/ai/AIProvider';

// ─── State & Actions ──────────────────────────────────────────────────────────

type Action =
  | { type: 'HYDRATE'; payload: Partial<AppState> }
  | { type: 'SET_CHATS'; chats: Chat[] }
  | { type: 'SET_SPACES'; spaces: Space[] }
  | { type: 'SET_CURRENT_CHAT'; chatId: string | null }
  | { type: 'SET_CURRENT_SPACE'; spaceId: string | null }
  | { type: 'UPSERT_CHAT'; chat: Chat }
  | { type: 'DELETE_CHAT'; chatId: string }
  | { type: 'UPSERT_SPACE'; space: Space }
  | { type: 'DELETE_SPACE'; spaceId: string }
  | { type: 'UPDATE_SETTINGS'; settings: Partial<AISettings> }
  | { type: 'SET_API_KEY'; provider: ProviderID; config: ApiKeyConfig | null }
  | { type: 'SET_THINKING_STATUS'; status: ThinkingStatus }
  | { type: 'SET_GENERATING'; value: boolean }
  | { type: 'APPEND_TOKEN'; chatId: string; spaceId?: string; messageId: string; token: string }
  | { type: 'FINALIZE_MESSAGE'; chatId: string; spaceId?: string; messageId: string; content: string };

const DEFAULT_SETTINGS: AISettings = {
  provider: 'openrouter',
  model: 'openai/gpt-4o',
  temperature: 0.7,
  maxTokens: 2048,
  thinkingEnabled: false,
  thinkingMode: 'direct',
  searchEnabled: false,
  searchMode: 'standard',
};

const INITIAL_STATE: AppState = {
  chats: [],
  spaces: [],
  currentChatId: null,
  currentSpaceId: null,
  settings: DEFAULT_SETTINGS,
  apiKeys: { openrouter: null, claude: null, deepseek: null, gemini: null, custom: null },
  thinkingStatus: 'idle',
  isGenerating: false,
};

function reducer(state: AppState, action: Action): AppState {
  switch (action.type) {
    case 'HYDRATE':
      return { ...state, ...action.payload };

    case 'SET_CHATS':
      return { ...state, chats: action.chats };

    case 'SET_SPACES':
      return { ...state, spaces: action.spaces };

    case 'SET_CURRENT_CHAT':
      return { ...state, currentChatId: action.chatId };

    case 'SET_CURRENT_SPACE':
      return { ...state, currentSpaceId: action.spaceId };

    case 'UPSERT_CHAT': {
      const idx = state.chats.findIndex(c => c.id === action.chat.id);
      const chats =
        idx >= 0
          ? state.chats.map((c, i) => (i === idx ? action.chat : c))
          : [action.chat, ...state.chats];
      return { ...state, chats };
    }

    case 'DELETE_CHAT': {
      const chats = state.chats.filter(c => c.id !== action.chatId);
      const currentChatId =
        state.currentChatId === action.chatId ? null : state.currentChatId;
      return { ...state, chats, currentChatId };
    }

    case 'UPSERT_SPACE': {
      const idx = state.spaces.findIndex(s => s.id === action.space.id);
      const spaces =
        idx >= 0
          ? state.spaces.map((s, i) => (i === idx ? action.space : s))
          : [action.space, ...state.spaces];
      return { ...state, spaces };
    }

    case 'DELETE_SPACE': {
      const spaces = state.spaces.filter(s => s.id !== action.spaceId);
      const currentSpaceId =
        state.currentSpaceId === action.spaceId ? null : state.currentSpaceId;
      return { ...state, spaces, currentSpaceId };
    }

    case 'UPDATE_SETTINGS':
      return { ...state, settings: { ...state.settings, ...action.settings } };

    case 'SET_API_KEY':
      return {
        ...state,
        apiKeys: { ...state.apiKeys, [action.provider]: action.config },
      };

    case 'SET_THINKING_STATUS':
      return { ...state, thinkingStatus: action.status };

    case 'SET_GENERATING':
      return { ...state, isGenerating: action.value };

    case 'APPEND_TOKEN': {
      const updater = (chat: Chat) => {
        if (chat.id !== action.chatId) return chat;
        const messages = chat.messages.map(m =>
          m.id === action.messageId
            ? { ...m, content: m.content + action.token }
            : m,
        );
        return { ...chat, messages };
      };
      if (action.spaceId) {
        const spaces = state.spaces.map(s => {
          if (s.id !== action.spaceId) return s;
          return { ...s, chats: s.chats.map(updater) };
        });
        return { ...state, spaces };
      }
      return { ...state, chats: state.chats.map(updater) };
    }

    case 'FINALIZE_MESSAGE': {
      const updater = (chat: Chat) => {
        if (chat.id !== action.chatId) return chat;
        const messages = chat.messages.map(m =>
          m.id === action.messageId
            ? { ...m, content: action.content, status: 'done' as const }
            : m,
        );
        return { ...chat, messages, updatedAt: Date.now() };
      };
      if (action.spaceId) {
        const spaces = state.spaces.map(s => {
          if (s.id !== action.spaceId) return s;
          return { ...s, chats: s.chats.map(updater) };
        });
        return { ...state, spaces };
      }
      return { ...state, chats: state.chats.map(updater) };
    }

    default:
      return state;
  }
}

// ─── Context ──────────────────────────────────────────────────────────────────

interface AppContextValue {
  state: AppState;
  startNewChat: (spaceId?: string) => Chat;
  sendMessage: (content: string, chatId?: string, spaceId?: string) => Promise<void>;
  editAndResend: (chatId: string, messageId: string, newContent: string, spaceId?: string) => Promise<void>;
  deleteChat: (chatId: string, spaceId?: string) => void;
  createSpace: (name: string, instructions: string) => Space;
  updateSpace: (space: Space) => void;
  deleteSpace: (spaceId: string) => void;
  updateSettings: (partial: Partial<AISettings>) => void;
  saveApiKey: (config: ApiKeyConfig) => Promise<void>;
  deleteApiKey: (provider: ProviderID) => Promise<void>;
  testApiKey: (config: ApiKeyConfig) => Promise<boolean>;
  toggleThinking: () => void;
  toggleSearch: () => void;
  stopGeneration: () => void;
  setCurrentChat: (chatId: string | null) => void;
  setCurrentSpace: (spaceId: string | null) => void;
}

const AppContext = createContext<AppContextValue | null>(null);

// ─── Provider ─────────────────────────────────────────────────────────────────

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(reducer, INITIAL_STATE);
  const abortControllerRef = useRef<AbortController | null>(null);
  const stateRef = useRef(state);
  stateRef.current = state;

  // Hydrate from storage
  useEffect(() => {
    (async () => {
      const [chats, spaces, savedSettings, apiKeysMap] = await Promise.all([
        StorageService.loadChats(),
        StorageService.loadSpaces(),
        StorageService.loadSettings(),
        StorageService.loadAllApiKeys(),
      ]);

      const apiKeys: AppState['apiKeys'] = {
        openrouter: apiKeysMap.openrouter ?? null,
        claude: apiKeysMap.claude ?? null,
        deepseek: apiKeysMap.deepseek ?? null,
        gemini: apiKeysMap.gemini ?? null,
        custom: apiKeysMap.custom ?? null,
      };

      dispatch({
        type: 'HYDRATE',
        payload: {
          chats,
          spaces,
          settings: savedSettings ?? DEFAULT_SETTINGS,
          apiKeys,
        },
      });
    })();
  }, []);

  // Persist chats
  useEffect(() => {
    StorageService.saveChats(state.chats);
  }, [state.chats]);

  // Persist spaces
  useEffect(() => {
    StorageService.saveSpaces(state.spaces);
  }, [state.spaces]);

  // Persist settings
  useEffect(() => {
    StorageService.saveSettings(state.settings);
  }, [state.settings]);

  // ── AI Provider factory ──────────────────────────────────────────────────

  const buildProvider = useCallback(
    (overrideProvider?: ProviderID): AIProvider | null => {
      const provider = overrideProvider ?? stateRef.current.settings.provider;
      const keyConfig = stateRef.current.apiKeys[provider];
      if (!keyConfig?.apiKey) return null;

      switch (provider) {
        case 'openrouter':
          return new OpenRouterProvider(keyConfig.apiKey);
        case 'claude':
          return new ClaudeProvider(keyConfig.apiKey);
        case 'deepseek':
          return new DeepSeekProvider(keyConfig.apiKey);
        case 'gemini':
          return new GeminiProvider(keyConfig.apiKey);
        case 'custom':
          return new CustomProvider(
            keyConfig.apiKey,
            keyConfig.baseUrl ?? '',
            keyConfig.customModelName ?? 'custom-model',
          );
        default:
          return null;
      }
    },
    [],
  );

  // ── Helpers ──────────────────────────────────────────────────────────────

  const getChatFromState = useCallback(
    (chatId: string, spaceId?: string): Chat | undefined => {
      if (spaceId) {
        const space = stateRef.current.spaces.find(s => s.id === spaceId);
        return space?.chats.find(c => c.id === chatId);
      }
      return stateRef.current.chats.find(c => c.id === chatId);
    },
    [],
  );

  const getSpaceForChat = useCallback((spaceId?: string): Space | undefined => {
    if (!spaceId) return undefined;
    return stateRef.current.spaces.find(s => s.id === spaceId);
  }, []);

  const buildSystemPrompt = useCallback(
    (spaceId?: string, searchContext?: string): string => {
      const space = getSpaceForChat(spaceId);
      let prompt = 'You are Classic AI, a helpful, accurate, and concise assistant.';
      if (space?.instructions) {
        prompt += `\n\nSpace Instructions:\n${space.instructions}`;
      }
      if (space?.files?.length) {
        const fileContext = space.files
          .map(f => `--- File: ${f.name} ---\n${f.content}`)
          .join('\n\n');
        prompt += `\n\nAttached Files:\n${fileContext}`;
      }
      if (space?.links?.length) {
        const linkContext = space.links.map(l => `- ${l.url}`).join('\n');
        prompt += `\n\nRelevant Links:\n${linkContext}`;
      }
      if (searchContext) {
        prompt += `\n\nSearch Results (use these to answer accurately):\n${searchContext}`;
      }
      return prompt;
    },
    [getSpaceForChat],
  );

  const generateTitle = (content: string): string => {
    const trimmed = content.trim();
    if (trimmed.length <= 40) return trimmed;
    return trimmed.slice(0, 37) + '...';
  };

  // ── Public API ───────────────────────────────────────────────────────────

  const startNewChat = useCallback((spaceId?: string): Chat => {
    const chat: Chat = {
      id: uuidv4(),
      title: 'New Chat',
      messages: [],
      createdAt: Date.now(),
      updatedAt: Date.now(),
      spaceId,
    };
    if (spaceId) {
      const space = stateRef.current.spaces.find(s => s.id === spaceId);
      if (space) {
        const updatedSpace: Space = {
          ...space,
          chats: [chat, ...space.chats],
          updatedAt: Date.now(),
        };
        dispatch({ type: 'UPSERT_SPACE', space: updatedSpace });
      }
    } else {
      dispatch({ type: 'UPSERT_CHAT', chat });
    }
    dispatch({ type: 'SET_CURRENT_CHAT', chatId: chat.id });
    return chat;
  }, []);

  const sendMessage = useCallback(
    async (content: string, chatId?: string, spaceId?: string) => {
      const { settings, apiKeys } = stateRef.current;

      // Determine or create a chat
      let activeChatId = chatId ?? stateRef.current.currentChatId;
      if (!activeChatId) {
        const newChat = startNewChat(spaceId);
        activeChatId = newChat.id;
      }

      const space = getSpaceForChat(spaceId);
      const effectiveProvider: ProviderID =
        (space?.settings.provider ?? settings.provider) as ProviderID;
      const effectiveModel =
        space?.settings.model ?? settings.model;
      const effectiveThinkingEnabled =
        space?.settings.thinkingEnabled ?? settings.thinkingEnabled;
      const effectiveSearchEnabled =
        space?.settings.searchEnabled ?? settings.searchEnabled;
      const effectiveSearchMode =
        space?.settings.searchMode ?? settings.searchMode;

      const userMsg: Message = {
        id: uuidv4(),
        role: 'user',
        content,
        createdAt: Date.now(),
        status: 'done',
      };

      const assistantMsg: Message = {
        id: uuidv4(),
        role: 'assistant',
        content: '',
        createdAt: Date.now(),
        status: 'sending',
      };

      // Insert user + placeholder assistant messages
      const existingChat = getChatFromState(activeChatId, spaceId);
      const updatedMessages = [...(existingChat?.messages ?? []), userMsg, assistantMsg];
      const isFirstMessage = (existingChat?.messages.length ?? 0) === 0;

      const updatedChat: Chat = {
        ...(existingChat ?? {
          id: activeChatId,
          createdAt: Date.now(),
          spaceId,
        }),
        title: isFirstMessage ? generateTitle(content) : (existingChat?.title ?? 'Chat'),
        messages: updatedMessages,
        updatedAt: Date.now(),
      };

      if (spaceId) {
        const s = stateRef.current.spaces.find(sp => sp.id === spaceId);
        if (s) {
          const chatIdx = s.chats.findIndex(c => c.id === activeChatId);
          const chats =
            chatIdx >= 0
              ? s.chats.map((c, i) => (i === chatIdx ? updatedChat : c))
              : [updatedChat, ...s.chats];
          dispatch({ type: 'UPSERT_SPACE', space: { ...s, chats, updatedAt: Date.now() } });
        }
      } else {
        dispatch({ type: 'UPSERT_CHAT', chat: updatedChat });
      }

      dispatch({ type: 'SET_CURRENT_CHAT', chatId: activeChatId });
      dispatch({ type: 'SET_GENERATING', value: true });

      // Abort controller for stop
      abortControllerRef.current = new AbortController();
      const signal = abortControllerRef.current.signal;

      try {
        // Thinking status phases
        dispatch({ type: 'SET_THINKING_STATUS', status: 'thinking' });
        await delay(300);
        dispatch({ type: 'SET_THINKING_STATUS', status: 'understanding' });

        // Search phase
        let searchContext: string | undefined;
        if (effectiveSearchEnabled && SearchService.shouldSearch(content)) {
          const mode = effectiveSearchMode;
          dispatch({
            type: 'SET_THINKING_STATUS',
            status: mode === 'extended' ? 'searching_deep' : 'searching',
          });
          const results =
            mode === 'extended'
              ? await SearchService.extendedSearch(content)
              : await SearchService.standardSearch(content);
          searchContext = SearchService.formatResultsForAI(results);
          dispatch({ type: 'SET_THINKING_STATUS', status: 'reviewing' });
          await delay(300);
        }

        dispatch({ type: 'SET_THINKING_STATUS', status: 'preparing' });
        await delay(200);

        const provider = buildProvider(effectiveProvider);
        if (!provider) {
          throw new Error(
            `No API key configured for provider: ${effectiveProvider}. Please add your API key in Settings > API Keys.`,
          );
        }

        const systemPrompt = buildSystemPrompt(spaceId, searchContext);
        const historyMessages = updatedMessages.filter(
          m => m.id !== assistantMsg.id,
        );

        dispatch({ type: 'SET_THINKING_STATUS', status: 'generating' });

        await provider.streamMessage({
          messages: historyMessages,
          model: effectiveModel,
          temperature: settings.temperature,
          maxTokens: settings.maxTokens,
          systemPrompt,
          useThinking: effectiveThinkingEnabled && provider.supportsThinking(),
          thinkingBudget: settings.thinkingMode === 'extended' ? 10000 : 5000,
          signal,
          streamCallback: {
            onToken: (token) => {
              dispatch({
                type: 'APPEND_TOKEN',
                chatId: activeChatId!,
                spaceId,
                messageId: assistantMsg.id,
                token,
              });
            },
            onComplete: (fullText) => {
              dispatch({
                type: 'FINALIZE_MESSAGE',
                chatId: activeChatId!,
                spaceId,
                messageId: assistantMsg.id,
                content: fullText,
              });
            },
            onError: (err) => {
              dispatch({
                type: 'FINALIZE_MESSAGE',
                chatId: activeChatId!,
                spaceId,
                messageId: assistantMsg.id,
                content: `Error: ${err.message}`,
              });
            },
          },
        });
      } catch (err: any) {
        if (err?.name !== 'AbortError') {
          const errorMsg = err?.message ?? 'An unexpected error occurred.';
          dispatch({
            type: 'FINALIZE_MESSAGE',
            chatId: activeChatId,
            spaceId,
            messageId: assistantMsg.id,
            content: `Error: ${errorMsg}`,
          });
        } else {
          // Stopped by user — finalise whatever was streamed
          const chat = getChatFromState(activeChatId, spaceId);
          const partial = chat?.messages.find(m => m.id === assistantMsg.id);
          dispatch({
            type: 'FINALIZE_MESSAGE',
            chatId: activeChatId,
            spaceId,
            messageId: assistantMsg.id,
            content: partial?.content ?? '(generation stopped)',
          });
        }
      } finally {
        dispatch({ type: 'SET_GENERATING', value: false });
        dispatch({ type: 'SET_THINKING_STATUS', status: 'idle' });
      }
    },
    [startNewChat, getChatFromState, getSpaceForChat, buildSystemPrompt, buildProvider],
  );

  const editAndResend = useCallback(
    async (chatId: string, messageId: string, newContent: string, spaceId?: string) => {
      const chat = getChatFromState(chatId, spaceId);
      if (!chat) return;

      const msgIdx = chat.messages.findIndex(m => m.id === messageId);
      if (msgIdx < 0) return;

      // Truncate messages from edited message onward
      const trimmedMessages = chat.messages.slice(0, msgIdx);
      const updatedChat: Chat = {
        ...chat,
        messages: trimmedMessages,
        updatedAt: Date.now(),
      };

      if (spaceId) {
        const s = stateRef.current.spaces.find(sp => sp.id === spaceId);
        if (s) {
          const chats = s.chats.map(c => (c.id === chatId ? updatedChat : c));
          dispatch({ type: 'UPSERT_SPACE', space: { ...s, chats } });
        }
      } else {
        dispatch({ type: 'UPSERT_CHAT', chat: updatedChat });
      }

      await sendMessage(newContent, chatId, spaceId);
    },
    [getChatFromState, sendMessage],
  );

  const deleteChat = useCallback((chatId: string, spaceId?: string) => {
    if (spaceId) {
      const s = stateRef.current.spaces.find(sp => sp.id === spaceId);
      if (s) {
        const chats = s.chats.filter(c => c.id !== chatId);
        dispatch({ type: 'UPSERT_SPACE', space: { ...s, chats } });
      }
    } else {
      dispatch({ type: 'DELETE_CHAT', chatId });
    }
  }, []);

  const createSpace = useCallback((name: string, instructions: string): Space => {
    const space: Space = {
      id: uuidv4(),
      name,
      instructions,
      files: [],
      links: [],
      settings: {
        searchEnabled: false,
        searchMode: 'standard',
        thinkingEnabled: false,
        thinkingMode: 'direct',
      },
      chats: [],
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };
    dispatch({ type: 'UPSERT_SPACE', space });
    return space;
  }, []);

  const updateSpace = useCallback((space: Space) => {
    dispatch({ type: 'UPSERT_SPACE', space: { ...space, updatedAt: Date.now() } });
  }, []);

  const deleteSpace = useCallback((spaceId: string) => {
    dispatch({ type: 'DELETE_SPACE', spaceId });
  }, []);

  const updateSettings = useCallback((partial: Partial<AISettings>) => {
    dispatch({ type: 'UPDATE_SETTINGS', settings: partial });
  }, []);

  const saveApiKey = useCallback(async (config: ApiKeyConfig) => {
    await StorageService.saveApiKey(config);
    dispatch({ type: 'SET_API_KEY', provider: config.provider, config });
  }, []);

  const deleteApiKey = useCallback(async (provider: ProviderID) => {
    await StorageService.deleteApiKey(provider);
    dispatch({ type: 'SET_API_KEY', provider, config: null });
  }, []);

  const testApiKey = useCallback(async (config: ApiKeyConfig): Promise<boolean> => {
    try {
      let provider: AIProvider;
      switch (config.provider) {
        case 'openrouter':
          provider = new OpenRouterProvider(config.apiKey);
          break;
        case 'claude':
          provider = new ClaudeProvider(config.apiKey);
          break;
        case 'deepseek':
          provider = new DeepSeekProvider(config.apiKey);
          break;
        case 'gemini':
          provider = new GeminiProvider(config.apiKey);
          break;
        case 'custom':
          provider = new CustomProvider(
            config.apiKey,
            config.baseUrl ?? '',
            config.customModelName,
          );
          break;
        default:
          return false;
      }
      return await provider.validateApiKey(config.apiKey);
    } catch {
      return false;
    }
  }, []);

  const toggleThinking = useCallback(() => {
    dispatch({
      type: 'UPDATE_SETTINGS',
      settings: { thinkingEnabled: !stateRef.current.settings.thinkingEnabled },
    });
  }, []);

  const toggleSearch = useCallback(() => {
    dispatch({
      type: 'UPDATE_SETTINGS',
      settings: { searchEnabled: !stateRef.current.settings.searchEnabled },
    });
  }, []);

  const stopGeneration = useCallback(() => {
    abortControllerRef.current?.abort();
  }, []);

  const setCurrentChat = useCallback((chatId: string | null) => {
    dispatch({ type: 'SET_CURRENT_CHAT', chatId });
  }, []);

  const setCurrentSpace = useCallback((spaceId: string | null) => {
    dispatch({ type: 'SET_CURRENT_SPACE', spaceId });
  }, []);

  return (
    <AppContext.Provider
      value={{
        state,
        startNewChat,
        sendMessage,
        editAndResend,
        deleteChat,
        createSpace,
        updateSpace,
        deleteSpace,
        updateSettings,
        saveApiKey,
        deleteApiKey,
        testApiKey,
        toggleThinking,
        toggleSearch,
        stopGeneration,
        setCurrentChat,
        setCurrentSpace,
      }}
    >
      {children}
    </AppContext.Provider>
  );
}

// ─── Hook ────────────────────────────────────────────────────────────────────

export function useApp(): AppContextValue {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used inside AppProvider');
  return ctx;
}

// ─── Utils ────────────────────────────────────────────────────────────────────

function delay(ms: number): Promise<void> {
  return new Promise(r => setTimeout(r, ms));
}
