import React, { useRef, useEffect, useCallback } from 'react';
import {
  View,
  FlatList,
  StyleSheet,
  TouchableOpacity,
  Text,
  SafeAreaView,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { DrawerNavigationProp } from '@react-navigation/drawer';
import { useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { useApp } from '../providers/AppProvider';
import { useChat } from '../hooks/useChat';
import { MessageBubble } from '../components/MessageBubble';
import { ChatInput } from '../components/ChatInput';
import { ThinkingStatus } from '../components/ThinkingStatus';
import { Colors, Spacing, FontSize, FontWeight } from '../constants/theme';
import { Message } from '../types';

interface Props {
  chatId?: string;
  spaceId?: string;
}

export function ChatScreen({ chatId, spaceId }: Props) {
  const navigation = useNavigation<DrawerNavigationProp<any>>();
  const { state, startNewChat, sendMessage, editAndResend, stopGeneration } = useApp();
  const flatListRef = useRef<FlatList>(null);

  const activeChatId = chatId ?? state.currentChatId ?? undefined;
  const { chat, messages, isGenerating, thinkingStatus, send, editMessage } = useChat(
    activeChatId,
    spaceId,
  );

  const { settings } = state;

  useEffect(() => {
    if (messages.length > 0) {
      setTimeout(() => {
        flatListRef.current?.scrollToEnd({ animated: true });
      }, 100);
    }
  }, [messages.length]);

  const handleSend = useCallback(
    (content: string) => {
      send(content);
    },
    [send],
  );

  const handleRegenerate = useCallback(() => {
    if (!activeChatId || !chat) return;
    const lastUserMsg = [...chat.messages].reverse().find(m => m.role === 'user');
    if (lastUserMsg) {
      editAndResend(activeChatId, lastUserMsg.id, lastUserMsg.content, spaceId);
    }
  }, [activeChatId, chat, editAndResend, spaceId]);

  const renderMessage = useCallback(
    ({ item, index }: { item: Message; index: number }) => (
      <MessageBubble
        message={item}
        isLast={index === messages.length - 1}
        onEdit={item.role === 'user' ? editMessage : undefined}
        onRegenerate={item.role === 'assistant' ? handleRegenerate : undefined}
      />
    ),
    [messages.length, editMessage, handleRegenerate],
  );

  const modelLabel =
    settings.model.includes('/')
      ? settings.model.split('/')[1]
      : settings.model;

  return (
    <SafeAreaView style={styles.safeArea}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 20}
      >
        {/* Header */}
        <View style={styles.header}>
          <TouchableOpacity
            onPress={() => navigation.openDrawer()}
            style={styles.headerBtn}
          >
            <Ionicons name="menu" size={24} color={Colors.text} />
          </TouchableOpacity>

          <View style={styles.headerCenter}>
            <Text style={styles.headerTitle} numberOfLines={1}>
              {chat?.title ?? 'Classic AI'}
            </Text>
            <Text style={styles.headerSub} numberOfLines={1}>{modelLabel}</Text>
          </View>

          <TouchableOpacity
            onPress={() => startNewChat(spaceId)}
            style={styles.headerBtn}
          >
            <Ionicons name="create-outline" size={22} color={Colors.text} />
          </TouchableOpacity>
        </View>

        {/* Messages */}
        {messages.length === 0 ? (
          <EmptyState />
        ) : (
          <FlatList
            ref={flatListRef}
            data={messages}
            keyExtractor={item => item.id}
            renderItem={renderMessage}
            contentContainerStyle={styles.listContent}
            onLayout={() => flatListRef.current?.scrollToEnd({ animated: false })}
          />
        )}

        {/* Thinking status */}
        <ThinkingStatus status={thinkingStatus} />

        {/* Input */}
        <ChatInput
          onSend={handleSend}
          onStop={stopGeneration}
          isGenerating={isGenerating}
          showAttach={false}
        />
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

function EmptyState() {
  return (
    <View style={emptyStyles.container}>
      <Text style={emptyStyles.title}>Classic AI</Text>
      <Text style={emptyStyles.sub}>How can I help you today?</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: Colors.background,
  },
  flex: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
    borderBottomWidth: 1,
    borderBottomColor: Colors.border,
    backgroundColor: Colors.background,
  },
  headerBtn: {
    padding: Spacing.xs,
  },
  headerCenter: {
    flex: 1,
    alignItems: 'center',
  },
  headerTitle: {
    color: Colors.text,
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
  },
  headerSub: {
    color: Colors.textMuted,
    fontSize: FontSize.xs,
  },
  listContent: {
    paddingVertical: Spacing.md,
    gap: Spacing.xs,
  },
});

const emptyStyles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.xxl,
  },
  title: {
    color: Colors.text,
    fontSize: FontSize.title,
    fontWeight: FontWeight.bold,
    marginBottom: Spacing.sm,
  },
  sub: {
    color: Colors.textSecondary,
    fontSize: FontSize.lg,
    textAlign: 'center',
  },
});
