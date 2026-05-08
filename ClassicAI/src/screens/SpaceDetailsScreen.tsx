import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  SafeAreaView,
  TextInput,
  Switch,
  Alert,
} from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { useApp } from '../providers/AppProvider';
import { useToast } from '../components/Toast';
import { Colors, FontSize, Spacing, BorderRadius, FontWeight } from '../constants/theme';
import { formatBytes } from '../utils/helpers';

export function SpaceDetailsScreen() {
  const navigation = useNavigation();
  const route = useRoute();
  const { spaceId } = (route.params as any) || {};
  const { state, updateSpace, deleteSpace } = useApp();
  const { show: showToast } = useToast();

  const space = state.spaces.find(s => s.id === spaceId);
  const [editingName, setEditingName] = useState(false);
  const [nameValue, setNameValue] = useState(space?.name || '');
  const [instructionsValue, setInstructionsValue] = useState(space?.instructions || '');

  if (!space) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.center}>
          <Text style={styles.errorText}>لم يتم العثور على المساحة</Text>
        </View>
      </SafeAreaView>
    );
  }

  const handleSaveName = () => {
    if (!nameValue.trim()) return;
    updateSpace({ ...space, name: nameValue.trim() });
    setEditingName(false);
    showToast('تم الحفظ', 'success');
  };

  const handleSaveInstructions = () => {
    updateSpace({ ...space, instructions: instructionsValue });
    showToast('تم حفظ التعليمات', 'success');
  };

  const handleDeleteFile = (fileId: string) => {
    const updated = space.files.filter(f => f.id !== fileId);
    updateSpace({ ...space, files: updated });
    showToast('تم حذف الملف', 'info');
  };

  const handleDeleteLink = (linkId: string) => {
    const updated = space.links.filter(l => l.id !== linkId);
    updateSpace({ ...space, links: updated });
    showToast('تم حذف الرابط', 'info');
  };

  const handleDeleteSpace = () => {
    Alert.alert(
      'حذف المساحة',
      `هل تريد حذف "${space.name}"؟ سيتم حذف جميع المحادثات والملفات.`,
      [
        { text: 'إلغاء', style: 'cancel' },
        {
          text: 'حذف', style: 'destructive',
          onPress: () => {
            deleteSpace(spaceId);
            navigation.goBack();
            showToast('تم حذف المساحة', 'info');
          },
        },
      ],
    );
  };

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Ionicons name="arrow-back" size={22} color={Colors.text} />
        </TouchableOpacity>
        <Text style={styles.title}>إعدادات المساحة</Text>
        <View style={{ width: 40 }} />
      </View>

      <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>

        {/* Name */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>الاسم</Text>
          {editingName ? (
            <View style={styles.editRow}>
              <TextInput
                style={[styles.input, styles.flex1]}
                value={nameValue}
                onChangeText={setNameValue}
                autoFocus
              />
              <TouchableOpacity style={styles.saveInlineBtn} onPress={handleSaveName}>
                <Ionicons name="checkmark" size={18} color={Colors.text} />
              </TouchableOpacity>
            </View>
          ) : (
            <TouchableOpacity style={styles.editableField} onPress={() => setEditingName(true)}>
              <Text style={styles.fieldValue}>{space.name}</Text>
              <Ionicons name="pencil-outline" size={16} color={Colors.textMuted} />
            </TouchableOpacity>
          )}
        </View>

        {/* Instructions */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>التعليمات</Text>
          <TextInput
            style={[styles.input, styles.textArea]}
            value={instructionsValue}
            onChangeText={setInstructionsValue}
            placeholder="تعليمات المساحة..."
            placeholderTextColor={Colors.textMuted}
            multiline
            numberOfLines={5}
            textAlignVertical="top"
          />
          <TouchableOpacity style={styles.saveBtn} onPress={handleSaveInstructions}>
            <Text style={styles.saveBtnText}>حفظ التعليمات</Text>
          </TouchableOpacity>
        </View>

        {/* Search Settings */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>إعدادات البحث</Text>
          <View style={styles.toggleRow}>
            <Text style={styles.toggleLabel}>تفعيل البحث</Text>
            <Switch
              value={space.settings.searchEnabled}
              onValueChange={v => updateSpace({ ...space, settings: { ...space.settings, searchEnabled: v } })}
              trackColor={{ false: Colors.border, true: Colors.accent }}
              thumbColor={Colors.text}
            />
          </View>
          {space.settings.searchEnabled && (
            <View style={styles.modeRow}>
              {(['standard', 'extended'] as const).map(mode => (
                <TouchableOpacity
                  key={mode}
                  style={[styles.modeBtn, space.settings.searchMode === mode && styles.modeBtnActive]}
                  onPress={() => updateSpace({ ...space, settings: { ...space.settings, searchMode: mode } })}
                >
                  <Text style={[styles.modeBtnText, space.settings.searchMode === mode && styles.modeBtnTextActive]}>
                    {mode === 'standard' ? 'عادي' : 'موسّع'}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          )}
        </View>

        {/* Thinking Settings */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>إعدادات التفكير</Text>
          <View style={styles.toggleRow}>
            <Text style={styles.toggleLabel}>تفعيل التفكير</Text>
            <Switch
              value={space.settings.thinkingEnabled}
              onValueChange={v => updateSpace({ ...space, settings: { ...space.settings, thinkingEnabled: v } })}
              trackColor={{ false: Colors.border, true: Colors.accent }}
              thumbColor={Colors.text}
            />
          </View>
          {space.settings.thinkingEnabled && (
            <View style={styles.modeRow}>
              {(['direct', 'extended'] as const).map(mode => (
                <TouchableOpacity
                  key={mode}
                  style={[styles.modeBtn, space.settings.thinkingMode === mode && styles.modeBtnActive]}
                  onPress={() => updateSpace({ ...space, settings: { ...space.settings, thinkingMode: mode } })}
                >
                  <Text style={[styles.modeBtnText, space.settings.thinkingMode === mode && styles.modeBtnTextActive]}>
                    {mode === 'direct' ? 'مباشر' : 'موسّع'}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          )}
        </View>

        {/* Files */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>الملفات ({space.files.length})</Text>
          {space.files.length === 0 ? (
            <Text style={styles.emptyText}>لا توجد ملفات مضافة</Text>
          ) : (
            space.files.map(file => (
              <View key={file.id} style={styles.fileRow}>
                <Ionicons name="document-outline" size={16} color={Colors.accent} />
                <View style={styles.fileInfo}>
                  <Text style={styles.fileName} numberOfLines={1}>{file.name}</Text>
                  <Text style={styles.fileSize}>{formatBytes(file.size)}</Text>
                </View>
                <TouchableOpacity onPress={() => handleDeleteFile(file.id)}>
                  <Ionicons name="trash-outline" size={16} color={Colors.danger} />
                </TouchableOpacity>
              </View>
            ))
          )}
        </View>

        {/* Links */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>الروابط ({space.links.length})</Text>
          {space.links.length === 0 ? (
            <Text style={styles.emptyText}>لا توجد روابط مضافة</Text>
          ) : (
            space.links.map(link => (
              <View key={link.id} style={styles.fileRow}>
                <Ionicons name="link-outline" size={16} color={Colors.accent} />
                <Text style={styles.linkUrl} numberOfLines={1}>{link.url}</Text>
                <TouchableOpacity onPress={() => handleDeleteLink(link.id)}>
                  <Ionicons name="trash-outline" size={16} color={Colors.danger} />
                </TouchableOpacity>
              </View>
            ))
          )}
        </View>

        {/* Delete Space */}
        <TouchableOpacity style={styles.deleteSpaceBtn} onPress={handleDeleteSpace}>
          <Ionicons name="trash-outline" size={18} color={Colors.danger} />
          <Text style={styles.deleteSpaceText}>حذف المساحة</Text>
        </TouchableOpacity>

      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: Colors.background },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  errorText: { color: Colors.textSecondary },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
    borderBottomWidth: 1,
    borderBottomColor: Colors.border,
  },
  backBtn: { padding: Spacing.sm },
  title: { fontSize: FontSize.lg, fontWeight: FontWeight.semibold, color: Colors.text },
  scroll: { flex: 1 },
  content: { padding: Spacing.md, gap: Spacing.md, paddingBottom: 60 },
  section: {
    backgroundColor: Colors.card,
    borderRadius: BorderRadius.lg,
    padding: Spacing.lg,
    gap: Spacing.md,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  sectionTitle: {
    fontSize: FontSize.sm,
    fontWeight: FontWeight.semibold,
    color: Colors.textMuted,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  editRow: { flexDirection: 'row', gap: Spacing.sm, alignItems: 'center' },
  flex1: { flex: 1 },
  editableField: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: Spacing.sm,
  },
  fieldValue: { fontSize: FontSize.md, color: Colors.text },
  saveInlineBtn: {
    width: 36,
    height: 36,
    borderRadius: BorderRadius.md,
    backgroundColor: Colors.accent + '33',
    alignItems: 'center',
    justifyContent: 'center',
  },
  input: {
    backgroundColor: Colors.inputBg,
    borderRadius: BorderRadius.md,
    borderWidth: 1,
    borderColor: Colors.border,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.md,
    fontSize: FontSize.md,
    color: Colors.text,
  },
  textArea: { minHeight: 100, paddingTop: Spacing.md, textAlignVertical: 'top' },
  saveBtn: {
    backgroundColor: Colors.accent + '33',
    paddingVertical: Spacing.md,
    borderRadius: BorderRadius.md,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.accent,
  },
  saveBtnText: { fontSize: FontSize.md, color: Colors.text, fontWeight: FontWeight.medium },
  toggleRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  toggleLabel: { fontSize: FontSize.md, color: Colors.text },
  modeRow: { flexDirection: 'row', gap: Spacing.sm },
  modeBtn: {
    flex: 1,
    paddingVertical: Spacing.sm,
    borderRadius: BorderRadius.md,
    borderWidth: 1,
    borderColor: Colors.border,
    alignItems: 'center',
  },
  modeBtnActive: { borderColor: Colors.accent, backgroundColor: Colors.accent + '22' },
  modeBtnText: { fontSize: FontSize.sm, color: Colors.textSecondary },
  modeBtnTextActive: { color: Colors.text, fontWeight: FontWeight.medium },
  fileRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    paddingVertical: Spacing.sm,
  },
  fileInfo: { flex: 1, gap: 2 },
  fileName: { fontSize: FontSize.sm, color: Colors.text },
  fileSize: { fontSize: FontSize.xs, color: Colors.textMuted },
  linkUrl: { flex: 1, fontSize: FontSize.sm, color: Colors.textSecondary },
  emptyText: { fontSize: FontSize.sm, color: Colors.textMuted, fontStyle: 'italic' },
  deleteSpaceBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.sm,
    backgroundColor: Colors.danger + '15',
    borderRadius: BorderRadius.lg,
    padding: Spacing.lg,
    borderWidth: 1,
    borderColor: Colors.danger + '33',
    marginTop: Spacing.md,
  },
  deleteSpaceText: { fontSize: FontSize.md, color: Colors.danger, fontWeight: FontWeight.medium },
});
