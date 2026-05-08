import React, { useRef, useEffect, useCallback, useState } from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  TouchableOpacity,
  SafeAreaView,
  KeyboardAvoidingView,
  Platform,
  Alert,
} from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system';
import * as Clipboard from 'expo-clipboard';
import { useApp } from '../providers/AppProvider';
import { useToast } from '../components/Toast';
import { MessageBubble } from '../components/MessageBubble';
import { ChatInput } from '../components/ChatInput';
import { ThinkingStatus } from '../components/ThinkingStatus';
import { Colors, FontSize, Spacing, BorderRadius, FontWeight } from '../constants/theme';
import { Message, SpaceFile, SpaceLink } from '../types';
import { generateId } from '../utils/helpers';

export function SpaceChatScreen() {
  const navigation = useNavigation();
  const route = useRoute();
  const { spaceId } = (route.params as any) || {};
  const { state, sendMessage, editAndResend, stopGeneration, updateSpace } = useApp();
  const { show: showToast } = useToast();
  const flatListRef = useRef<FlatList>(null);

  const space = state.spaces.find(s => s.id === spaceId);
  const currentChatId = state.currentChatId;
  const currentChat = space?.chats.find(c => c.id === currentChatId);
  const messages = currentChat?.messages ?? [];

  useEffect(() => {
    if (messages.length > 0) {
      setTimeout(() => flatListRef.current?.scrollToEnd({ animated: true }), 100);
    }
  }, [messages.length, state.thinkingStatus]);

  const handleSend = useCallback(async (text: string) => {
    if (!spaceId) return;
    try {
      await sendMessage(text, currentChatId ?? undefined, spaceId);
    } catch (err: any) {
      showToast(err?.message ?? 'حدث خطأ', 'error');
    }
  }, [sendMessage, currentChatId, spaceId, showToast]);

  const handleCopy = useCallback(async (text: string) => {
    await Clipboard.setStringAsync(text);
    showToast('تم نسخ النص', 'success');
  }, [showToast]);

  const handleEdit = useCallback(async (messageId: string, newContent: string) => {
    if (!currentChatId || !spaceId) return;
    await editAndResend(currentChatId, messageId, newContent, spaceId);
  }, [currentChatId, spaceId, editAndResend]);

  const handleRegenerate = useCallback(async (messageId: string) => {
    if (!currentChatId || !spaceId) return;
    const chat = space?.chats.find(c => c.id === currentChatId);
    if (!chat) return;
    const msgIdx = chat.messages.findIndex(m => m.id === messageId);
    if (msgIdx < 1) return;
    const prevUser = chat.messages.slice(0, msgIdx).reverse().find(m => m.role === 'user');
    if (!prevUser) return;
    await editAndResend(currentChatId, prevUser.id, prevUser.content, spaceId);
  }, [currentChatId, spaceId, space, editAndResend]);

  const handleAddFile = async () => {
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: ['text/plain', 'application/pdf', 'text/markdown'],
        copyToCacheDirectory: true,
      });
      if (result.canceled || !result.assets?.length) return;
      const asset = result.assets[0];
      let content = '';
      try {
        content = await FileSystem.readAsStringAsync(asset.uri);
      } catch {
        content = `[ملف: ${asset.name}]`;
      }
      if (!space) return;
      const file: SpaceFile = {
        id: generateId(),
        name: asset.name,
        content: content.slice(0, 20000),
        type: 'txt',
        size: asset.size || 0,
        addedAt: Date.now(),
      };
      updateSpace({ ...space, files: [...space.files, file], updatedAt: Date.now() });
      showToast(`تمت إضافة ${asset.name}`, 'success');
    } catch {
      showToast('فشل رفع الملف', 'error');
    }
  };

  const handleAddLink = () => {
    Alert.prompt(
      'إضافة رابط',
      'أدخل رابط الموقع',
      [
        { text: 'إلغاء', style: 'cancel' },
        {
          text: 'إضافة',
          onPress: (url?: string) => {
            if (!url?.trim() || !space) return;
            const link: SpaceLink = {
              id: generateId(),
              url: url.trim(),
              addedAt: Date.now(),
            };
            updateSpace({ ...space, links: [...space.links, link], updatedAt: Date.now() });
            showToast('تمت إضافة الرابط', 'success');
          },
        },
      ],
      'plain-text',
    );
  };

  if (!space) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.center}>
          <Text style={styles.errorText}>لم يتم العثور على المساحة</Text>
        </View>
      </SafeAreaView>
    );
  }

  const renderMessage = ({ item }: { item: Message }) => (
    <MessageBubble
      message={item}
      onCopy={handleCopy}
      onEdit={item.role === 'user' ? handleEdit : undefined}
      onRegenerate={item.role === 'assistant' ? handleRegenerate : undefined}
      isGenerating={state.isGenerating && item.status === 'sending'}
    />
  );

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.headerBtn}>
          <Ionicons name="arrow-back" size={22} color={Colors.text} />
        </TouchableOpacity>

        <View style={styles.headerCenter}>
          <Text style={styles.spaceName} numberOfLines={1}>{space.name}</Text>
          <Text style={styles.spaceStats}>
            {space.files.length} ملفات · {space.links.length} روابط
          </Text>
        </View>

        <TouchableOpacity
          onPress={() => (navigation as any).navigate('SpaceDetails', { spaceId })}
          style={styles.headerBtn}
        >
          <Ionicons name="settings-outline" size={20} color={Colors.text} />
        </TouchableOpacity>
      </View>

      {/* Attachments bar */}
      <View style={styles.attachBar}>
        <TouchableOpacity style={styles.attachBtn} onPress={handleAddFile}>
          <Ionicons name="document-attach-outline" size={15} color={Colors.accent} />
          <Text style={styles.attachBtnText}>ملف</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.attachBtn} onPress={handleAddLink}>
          <Ionicons name="link-outline" size={15} color={Colors.accent} />
          <Text style={styles.attachBtnText}>رابط</Text>
        </TouchableOpacity>
        {space.files.length > 0 && (
          <View style={styles.attachedCount}>
            <Ionicons name="document" size={12} color={Colors.textMuted} />
            <Text style={styles.attachedCountText}>{space.files.length}</Text>
          </View>
        )}
      </View>

      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        {messages.length === 0 ? (
          <View style={styles.emptyState}>
            <View style={styles.emptyIcon}>
              <Ionicons name="folder" size={40} color={Colors.accent} />
            </View>
            <Text style={styles.emptyTitle}>{space.name}</Text>
            {space.instructions ? (
              <Text style={styles.emptyInstructions} numberOfLines={3}>
                {space.instructions}
              </Text>
            ) : (
              <Text style={styles.emptySubtitle}>ابدأ محادثة في هذه المساحة</Text>
            )}
          </View>
        ) : (
          <FlatList
            ref={flatListRef}
            data={messages}
            keyExtractor={m => m.id}
            renderItem={renderMessage}
            contentContainerStyle={styles.messageList}
            showsVerticalScrollIndicator={false}
            ListFooterComponent={
              state.thinkingStatus !== 'idle' ? (
                <ThinkingStatus status={state.thinkingStatus} />
              ) : null
            }
          />
        )}

        <ChatInput
          onSend={handleSend}
          onStop={stopGeneration}
          isGenerating={state.isGenerating}
          disabled={false}
        />
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: Colors.background },
  flex: { flex: 1 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  errorText: { color: Colors.textSecondary, fontSize: FontSize.md },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
    borderBottomWidth: 1,
    borderBottomColor: Colors.border,
  },
  headerBtn: { padding: Spacing.sm },
  headerCenter: { flex: 1, alignItems: 'center', gap: 2 },
  spaceName: { fontSize: FontSize.md, fontWeight: FontWeight.semibold, color: Colors.text },
  spaceStats: { fontSize: FontSize.xs, color: Colors.textMuted },
  attachBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
    borderBottomWidth: 1,
    borderBottomColor: Colors.border,
    backgroundColor: Colors.backgroundAlt,
  },
  attachBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.xs,
    borderRadius: BorderRadius.full,
    borderWidth: 1,
    borderColor: Colors.accent + '44',
    backgroundColor: Colors.accent + '11',
  },
  attachBtnText: { fontSize: FontSize.xs, color: Colors.accent },
  attachedCount: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 2,
    marginLeft: Spacing.sm,
  },
  attachedCountText: { fontSize: FontSize.xs, color: Colors.textMuted },
  messageList: { padding: Spacing.md, paddingBottom: Spacing.xl },
  emptyState: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: Spacing.md, padding: Spacing.xl },
  emptyIcon: {
    width: 80,
    height: 80,
    borderRadius: BorderRadius.xl,
    backgroundColor: Colors.card,
    borderWidth: 1,
    borderColor: Colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyTitle: { fontSize: FontSize.xl, fontWeight: FontWeight.bold, color: Colors.text },
  emptySubtitle: { fontSize: FontSize.md, color: Colors.textSecondary },
  emptyInstructions: {
    fontSize: FontSize.sm,
    color: Colors.textSecondary,
    textAlign: 'center',
    lineHeight: 22,
    maxWidth: 280,
  },
});
