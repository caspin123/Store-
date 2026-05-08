import { useCallback } from 'react';
import { useApp } from '../providers/AppProvider';
import { Chat, Message } from '../types';

export function useChat(chatId?: string, spaceId?: string) {
  const { state, sendMessage, editAndResend, deleteChat, startNewChat, stopGeneration } = useApp();

  const chat: Chat | undefined = spaceId
    ? state.spaces.find(s => s.id === spaceId)?.chats.find(c => c.id === chatId)
    : state.chats.find(c => c.id === chatId);

  const messages: Message[] = chat?.messages ?? [];
  const isGenerating = state.isGenerating;
  const thinkingStatus = state.thinkingStatus;

  const send = useCallback(
    (content: string) => sendMessage(content, chatId, spaceId),
    [sendMessage, chatId, spaceId],
  );

  const editMessage = useCallback(
    (messageId: string, newContent: string) => {
      if (!chatId) return Promise.resolve();
      return editAndResend(chatId, messageId, newContent, spaceId);
    },
    [editAndResend, chatId, spaceId],
  );

  const removeChat = useCallback(() => {
    if (!chatId) return;
    deleteChat(chatId, spaceId);
  }, [deleteChat, chatId, spaceId]);

  const newChat = useCallback(() => startNewChat(spaceId), [startNewChat, spaceId]);

  return {
    chat,
    messages,
    isGenerating,
    thinkingStatus,
    send,
    editMessage,
    deleteChat: removeChat,
    newChat,
    stopGeneration,
  };
}
