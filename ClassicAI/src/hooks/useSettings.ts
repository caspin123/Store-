import { useCallback } from 'react';
import { useApp } from '../providers/AppProvider';
import { AISettings, ApiKeyConfig, ProviderID } from '../types';
import { PROVIDER_MODELS, PROVIDER_NAMES } from '../constants/providers';

export function useSettings() {
  const {
    state,
    updateSettings,
    saveApiKey,
    deleteApiKey,
    testApiKey,
    toggleThinking,
    toggleSearch,
  } = useApp();

  const { settings, apiKeys } = state;

  const setProvider = useCallback(
    (provider: ProviderID) => {
      const models = PROVIDER_MODELS[provider];
      updateSettings({ provider, model: models[0]?.id ?? '' });
    },
    [updateSettings],
  );

  const setModel = useCallback(
    (model: string) => updateSettings({ model }),
    [updateSettings],
  );

  const setTemperature = useCallback(
    (temperature: number) => updateSettings({ temperature }),
    [updateSettings],
  );

  const setMaxTokens = useCallback(
    (maxTokens: number) => updateSettings({ maxTokens }),
    [updateSettings],
  );

  const setThinkingMode = useCallback(
    (thinkingMode: AISettings['thinkingMode']) => updateSettings({ thinkingMode }),
    [updateSettings],
  );

  const setSearchMode = useCallback(
    (searchMode: AISettings['searchMode']) => updateSettings({ searchMode }),
    [updateSettings],
  );

  const availableModels = PROVIDER_MODELS[settings.provider] ?? [];
  const providerName = PROVIDER_NAMES[settings.provider] ?? settings.provider;

  const hasApiKey = useCallback(
    (provider: ProviderID) => !!apiKeys[provider]?.apiKey,
    [apiKeys],
  );

  return {
    settings,
    apiKeys,
    availableModels,
    providerName,
    setProvider,
    setModel,
    setTemperature,
    setMaxTokens,
    setThinkingMode,
    setSearchMode,
    toggleThinking,
    toggleSearch,
    saveApiKey,
    deleteApiKey,
    testApiKey,
    hasApiKey,
    updateSettings,
  };
}
