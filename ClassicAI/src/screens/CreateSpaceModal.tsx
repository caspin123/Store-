import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  SafeAreaView,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { useApp } from '../providers/AppProvider';
import { useToast } from '../components/Toast';
import { Colors, FontSize, Spacing, BorderRadius, FontWeight } from '../constants/theme';

export function CreateSpaceModal() {
  const navigation = useNavigation();
  const { createSpace } = useApp();
  const { show: showToast } = useToast();

  const [name, setName] = useState('');
  const [instructions, setInstructions] = useState('');
  const [creating, setCreating] = useState(false);

  const handleCreate = async () => {
    if (!name.trim()) {
      showToast('يرجى إدخال اسم المساحة', 'error');
      return;
    }
    setCreating(true);
    try {
      const space = createSpace(name.trim(), instructions.trim());
      showToast('تم إنشاء المساحة', 'success');
      (navigation as any).replace('SpaceChat', { spaceId: space.id });
    } finally {
      setCreating(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <View style={styles.header}>
          <TouchableOpacity onPress={() => navigation.goBack()} style={styles.cancelBtn}>
            <Text style={styles.cancelText}>إلغاء</Text>
          </TouchableOpacity>
          <Text style={styles.title}>مساحة جديدة</Text>
          <TouchableOpacity
            style={[styles.createBtn, !name.trim() && styles.createBtnDisabled]}
            onPress={handleCreate}
            disabled={!name.trim() || creating}
          >
            <Text style={styles.createBtnText}>إنشاء</Text>
          </TouchableOpacity>
        </View>

        <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>
          <View style={styles.iconRow}>
            <View style={styles.spaceIcon}>
              <Ionicons name="folder" size={36} color={Colors.accent} />
            </View>
          </View>

          <View style={styles.field}>
            <Text style={styles.label}>اسم المساحة</Text>
            <TextInput
              style={styles.input}
              value={name}
              onChangeText={setName}
              placeholder="مثال: أبحاث طبية، مشروع تخرج..."
              placeholderTextColor={Colors.textMuted}
              autoFocus
              maxLength={50}
            />
          </View>

          <View style={styles.field}>
            <Text style={styles.label}>التعليمات</Text>
            <TextInput
              style={[styles.input, styles.textArea]}
              value={instructions}
              onChangeText={setInstructions}
              placeholder="اكتب هنا فائدة هذه المساحة، وعلى ماذا تريد أن يركز الذكاء الاصطناعي. مثلاً: هذه المساحة مخصصة للأبحاث الطبية، أو لتلخيص ملفات الجامعة، أو لتحليل مواقع المنافسين."
              placeholderTextColor={Colors.textMuted}
              multiline
              numberOfLines={6}
              textAlignVertical="top"
            />
          </View>

          <View style={styles.hint}>
            <Ionicons name="information-circle-outline" size={16} color={Colors.textMuted} />
            <Text style={styles.hintText}>
              ستستخدم هذه التعليمات كسياق لكل محادثة داخل المساحة.
            </Text>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: Colors.background },
  flex: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: Colors.border,
  },
  cancelBtn: { padding: Spacing.sm },
  cancelText: { fontSize: FontSize.md, color: Colors.textSecondary },
  title: { fontSize: FontSize.lg, fontWeight: FontWeight.semibold, color: Colors.text },
  createBtn: {
    backgroundColor: Colors.accent,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.sm,
    borderRadius: BorderRadius.md,
  },
  createBtnDisabled: { opacity: 0.4 },
  createBtnText: { fontSize: FontSize.md, color: Colors.text, fontWeight: FontWeight.semibold },
  scroll: { flex: 1 },
  content: { padding: Spacing.xl, gap: Spacing.xl, paddingBottom: 60 },
  iconRow: { alignItems: 'center', marginBottom: Spacing.sm },
  spaceIcon: {
    width: 72,
    height: 72,
    borderRadius: BorderRadius.xl,
    backgroundColor: Colors.card,
    borderWidth: 1,
    borderColor: Colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  field: { gap: Spacing.sm },
  label: { fontSize: FontSize.sm, fontWeight: FontWeight.medium, color: Colors.textSecondary },
  input: {
    backgroundColor: Colors.card,
    borderRadius: BorderRadius.md,
    borderWidth: 1,
    borderColor: Colors.border,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.md,
    fontSize: FontSize.md,
    color: Colors.text,
  },
  textArea: {
    minHeight: 140,
    paddingTop: Spacing.md,
  },
  hint: {
    flexDirection: 'row',
    gap: Spacing.sm,
    alignItems: 'flex-start',
  },
  hintText: { flex: 1, fontSize: FontSize.sm, color: Colors.textMuted, lineHeight: 20 },
});
