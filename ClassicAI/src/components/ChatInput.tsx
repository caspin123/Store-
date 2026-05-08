import React, { useState, useRef, useCallback } from 'react';
import {
  View,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Platform,
  ActivityIndicator,
  Text,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Colors, Spacing, BorderRadius, FontSize } from '../constants/theme';

interface Props {
  onSend: (content: string) => void;
  onStop?: () => void;
  onAttach?: () => void;
  isGenerating?: boolean;
  showAttach?: boolean;
  placeholder?: string;
  disabled?: boolean;
}

export function ChatInput({
  onSend,
  onStop,
  onAttach,
  isGenerating = false,
  showAttach = false,
  placeholder = 'Message Classic AI...',
  disabled = false,
}: Props) {
  const [text, setText] = useState('');
  const inputRef = useRef<TextInput>(null);

  const handleSend = useCallback(() => {
    const trimmed = text.trim();
    if (!trimmed || isGenerating) return;
    onSend(trimmed);
    setText('');
  }, [text, isGenerating, onSend]);

  const handleStop = useCallback(() => {
    onStop?.();
  }, [onStop]);

  const canSend = text.trim().length > 0 && !isGenerating && !disabled;

  return (
    <View style={styles.wrapper}>
      <View style={styles.container}>
        {showAttach && (
          <TouchableOpacity onPress={onAttach} style={styles.attachBtn} disabled={disabled}>
            <Ionicons name="attach" size={22} color={Colors.textMuted} />
          </TouchableOpacity>
        )}

        <TextInput
          ref={inputRef}
          style={styles.input}
          value={text}
          onChangeText={setText}
          placeholder={placeholder}
          placeholderTextColor={Colors.textMuted}
          multiline
          maxLength={8000}
          returnKeyType="default"
          blurOnSubmit={false}
          onSubmitEditing={Platform.OS === 'web' ? handleSend : undefined}
          editable={!disabled}
        />

        {isGenerating ? (
          <TouchableOpacity onPress={handleStop} style={[styles.sendBtn, styles.stopBtn]}>
            <Ionicons name="stop" size={18} color={Colors.text} />
          </TouchableOpacity>
        ) : (
          <TouchableOpacity
            onPress={handleSend}
            style={[styles.sendBtn, canSend && styles.sendBtnActive]}
            disabled={!canSend}
          >
            <Ionicons
              name="arrow-up"
              size={18}
              color={canSend ? Colors.text : Colors.textMuted}
            />
          </TouchableOpacity>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
    backgroundColor: Colors.background,
    borderTopWidth: 1,
    borderTopColor: Colors.border,
  },
  container: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    backgroundColor: Colors.inputBg,
    borderRadius: BorderRadius.xl,
    borderWidth: 1,
    borderColor: Colors.border,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
    gap: Spacing.sm,
  },
  attachBtn: {
    padding: 4,
    marginBottom: 2,
  },
  input: {
    flex: 1,
    color: Colors.text,
    fontSize: FontSize.md,
    maxHeight: 120,
    minHeight: 36,
    textAlignVertical: 'center',
    paddingTop: 0,
    paddingBottom: 0,
  },
  sendBtn: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: Colors.card,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendBtnActive: {
    backgroundColor: Colors.accent,
  },
  stopBtn: {
    backgroundColor: Colors.danger,
  },
});
