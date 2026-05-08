import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  TextInput,
} from 'react-native';
import * as Clipboard from 'expo-clipboard';
import { Ionicons } from '@expo/vector-icons';
import { Message } from '../types';
import { Colors, Spacing, BorderRadius, FontSize, FontWeight } from '../constants/theme';
import { MarkdownRenderer } from './MarkdownRenderer';

interface Props {
  message: Message;
  onRegenerate?: ((messageId?: string) => void) | (() => void);
  onEdit?: (messageId: string, newContent: string) => void;
  onCopy?: (text: string) => void;
  isLast?: boolean;
  isGenerating?: boolean;
}

export function MessageBubble({ message, onRegenerate, onEdit, onCopy, isLast, isGenerating }: Props) {
  const [isEditing, setIsEditing] = useState(false);
  const [editText, setEditText] = useState(message.content);
  const [showActions, setShowActions] = useState(false);

  const isUser = message.role === 'user';

  const handleCopy = useCallback(async () => {
    await Clipboard.setStringAsync(message.content);
    onCopy?.(message.content);
  }, [message.content, onCopy]);

  const handleEditStart = useCallback(() => {
    setEditText(message.content);
    setIsEditing(true);
    setShowActions(false);
  }, [message.content]);

  const handleEditSubmit = useCallback(() => {
    if (editText.trim() && editText.trim() !== message.content) {
      onEdit?.(message.id, editText.trim());
    }
    setIsEditing(false);
  }, [editText, message.id, message.content, onEdit]);

  const handleEditCancel = useCallback(() => {
    setEditText(message.content);
    setIsEditing(false);
  }, [message.content]);

  if (isUser) {
    return (
      <View style={styles.userWrapper}>
        {isEditing ? (
          <View style={styles.editContainer}>
            <TextInput
              style={styles.editInput}
              value={editText}
              onChangeText={setEditText}
              multiline
              autoFocus
              placeholderTextColor={Colors.textMuted}
            />
            <View style={styles.editActions}>
              <TouchableOpacity onPress={handleEditCancel} style={styles.editBtn}>
                <Text style={styles.editBtnTextCancel}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity onPress={handleEditSubmit} style={[styles.editBtn, styles.editBtnSend]}>
                <Text style={styles.editBtnTextSend}>Send</Text>
              </TouchableOpacity>
            </View>
          </View>
        ) : (
          <TouchableOpacity
            activeOpacity={0.8}
            onLongPress={() => setShowActions(v => !v)}
            style={styles.userBubble}
          >
            <Text style={styles.userText} selectable>{message.content}</Text>
          </TouchableOpacity>
        )}

        {showActions && !isEditing && (
          <View style={styles.userActions}>
            <ActionButton icon="copy-outline" label="Copy" onPress={() => { handleCopy(); setShowActions(false); }} />
            {onEdit && (
              <ActionButton icon="pencil-outline" label="Edit" onPress={handleEditStart} />
            )}
          </View>
        )}
      </View>
    );
  }

  // AI message
  return (
    <View style={styles.aiWrapper}>
      <View style={styles.aiBubble}>
        {message.status === 'sending' && !message.content ? (
          <Text style={styles.cursorBlink}>▌</Text>
        ) : (
          <MarkdownRenderer content={message.content} />
        )}
        {message.status === 'sending' && message.content ? (
          <Text style={styles.cursor}>▌</Text>
        ) : null}
      </View>

      {(message.status === 'done' || message.status === 'error') && (
        <View style={styles.aiActions}>
          <ActionButton icon="copy-outline" label="Copy" onPress={handleCopy} />
          {onRegenerate && isLast && (
            <ActionButton icon="refresh-outline" label="Regenerate" onPress={() => (onRegenerate as any)(message.id)} />
          )}
        </View>
      )}
    </View>
  );
}

function ActionButton({
  icon,
  label,
  onPress,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  label: string;
  onPress: () => void;
}) {
  return (
    <TouchableOpacity style={actionStyles.btn} onPress={onPress}>
      <Ionicons name={icon} size={14} color={Colors.textMuted} />
      <Text style={actionStyles.label}>{label}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  userWrapper: {
    alignItems: 'flex-end',
    marginVertical: Spacing.xs,
    marginHorizontal: Spacing.lg,
  },
  userBubble: {
    backgroundColor: Colors.userBubble,
    borderRadius: BorderRadius.lg,
    borderBottomRightRadius: 4,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
    maxWidth: '85%',
  },
  userText: {
    color: Colors.text,
    fontSize: FontSize.md,
    lineHeight: 22,
  },
  userActions: {
    flexDirection: 'row',
    gap: Spacing.sm,
    marginTop: 4,
  },
  editContainer: {
    width: '90%',
  },
  editInput: {
    backgroundColor: Colors.inputBg,
    borderRadius: BorderRadius.md,
    borderWidth: 1,
    borderColor: Colors.border,
    color: Colors.text,
    fontSize: FontSize.md,
    padding: Spacing.md,
    minHeight: 80,
    textAlignVertical: 'top',
  },
  editActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: Spacing.sm,
    marginTop: Spacing.sm,
  },
  editBtn: {
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.xs,
    borderRadius: BorderRadius.sm,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  editBtnSend: {
    backgroundColor: Colors.accent,
    borderColor: Colors.accent,
  },
  editBtnTextCancel: {
    color: Colors.textSecondary,
    fontSize: FontSize.sm,
  },
  editBtnTextSend: {
    color: Colors.text,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.medium,
  },
  aiWrapper: {
    marginVertical: Spacing.xs,
    marginHorizontal: Spacing.md,
  },
  aiBubble: {
    backgroundColor: Colors.aiBubble,
    borderRadius: BorderRadius.lg,
    borderBottomLeftRadius: 4,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
  },
  cursor: {
    color: Colors.accent,
    fontSize: FontSize.md,
  },
  cursorBlink: {
    color: Colors.accent,
    fontSize: FontSize.lg,
  },
  aiActions: {
    flexDirection: 'row',
    gap: Spacing.sm,
    marginTop: 4,
    marginLeft: Spacing.xs,
  },
});

const actionStyles = StyleSheet.create({
  btn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    paddingHorizontal: Spacing.sm,
    paddingVertical: 3,
    borderRadius: BorderRadius.sm,
    backgroundColor: Colors.card,
  },
  label: {
    color: Colors.textMuted,
    fontSize: FontSize.xs,
  },
});
