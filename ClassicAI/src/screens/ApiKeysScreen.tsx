import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TextInput,
  TouchableOpacity,
  SafeAreaView,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { useApp } from '../providers/AppProvider';
import { useToast } from '../components/Toast';
import { Colors, FontSize, Spacing, BorderRadius, FontWeight } from '../constants/theme';
import { ProviderID, ApiKeyConfig } from '../types';
import { PROVIDER_NAMES } from '../constants/providers';

const PROVIDERS: ProviderID[] = ['openrouter', 'claude', 'deepseek', 'gemini', 'custom'];

export function ApiKeysScreen() {
  const navigation = useNavigation();
  const { state, saveApiKey, deleteApiKey, testApiKey, updateSettings } = useApp();
  const { show: showToast } = useToast();

  const [editingProvider, setEditingProvider] = useState<ProviderID | null>(null);
  const [apiKeyInput, setApiKeyInput] = useState('');
  const [baseUrlInput, setBaseUrlInput] = useState('');
  const [modelNameInput, setModelNameInput] = useState('');
  const [testing, setTesting] = useState<ProviderID | null>(null);
  const [showKey, setShowKey] = useState<ProviderID | null>(null);

  const startEditing = (provider: ProviderID) => {
    const existing = state.apiKeys[provider];
    setApiKeyInput(existing?.apiKey || '');
    setBaseUrlInput(existing?.baseUrl || '');
    setModelNameInput(existing?.customModelName || '');
    setEditingProvider(provider);
  };

  const cancelEditing = () => {
    setEditingProvider(null);
    setApiKeyInput('');
    setBaseUrlInput('');
    setModelNameInput('');
  };

  const handleSave = async (provider: ProviderID) => {
    if (!apiKeyInput.trim()) {
      showToast('يرجى إدخال API Key', 'error');
      return;
    }
    const config: ApiKeyConfig = {
      provider,
      apiKey: apiKeyInput.trim(),
      baseUrl: baseUrlInput.trim() || undefined,
      customModelName: modelNameInput.trim() || undefined,
    };
    await saveApiKey(config);
    updateSettings({ provider });
    showToast('تم حفظ API Key', 'success');
    cancelEditing();
  };

  const handleDelete = (provider: ProviderID) => {
    Alert.alert(
      'حذف API Key',
      `هل تريد حذف مفتاح ${PROVIDER_NAMES[provider]}؟`,
      [
        { text: 'إلغاء', style: 'cancel' },
        {
          text: 'حذف', style: 'destructive',
          onPress: async () => {
            await deleteApiKey(provider);
            showToast('تم الحذف', 'info');
          },
        },
      ],
    );
  };

  const handleTest = async (provider: ProviderID) => {
    const config = state.apiKeys[provider];
    if (!config) return;
    setTesting(provider);
    try {
      const ok = await testApiKey(config);
      showToast(ok ? 'API Key صالح ✓' : 'فشل اختبار API Key', ok ? 'success' : 'error');
    } catch {
      showToast('فشل الاختبار', 'error');
    } finally {
      setTesting(null);
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Ionicons name="arrow-back" size={22} color={Colors.text} />
        </TouchableOpacity>
        <Text style={styles.title}>إعدادات API</Text>
        <View style={{ width: 40 }} />
      </View>

      <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>
        {PROVIDERS.map(provider => {
          const keyConfig = state.apiKeys[provider];
          const isEditing = editingProvider === provider;
          const isActive = state.settings.provider === provider;

          return (
            <View key={provider} style={[styles.card, isActive && styles.cardActive]}>
              <View style={styles.cardHeader}>
                <View style={styles.cardLeft}>
                  <Text style={styles.providerName}>{PROVIDER_NAMES[provider]}</Text>
                  {isActive && (
                    <View style={styles.activeBadge}>
                      <Text style={styles.activeBadgeText}>نشط</Text>
                    </View>
                  )}
                </View>
                {keyConfig && (
                  <View style={styles.cardRight}>
                    <View style={styles.connectedDot} />
                    <Text style={styles.connectedText}>متصل</Text>
                  </View>
                )}
              </View>

              {!isEditing && (
                <>
                  {keyConfig ? (
                    <View style={styles.keyPreview}>
                      <Text style={styles.keyMask}>
                        {showKey === provider
                          ? keyConfig.apiKey
                          : keyConfig.apiKey.slice(0, 8) + '••••••••••••'}
                      </Text>
                      <TouchableOpacity onPress={() => setShowKey(prev => prev === provider ? null : provider)}>
                        <Ionicons
                          name={showKey === provider ? 'eye-off-outline' : 'eye-outline'}
                          size={16}
                          color={Colors.textMuted}
                        />
                      </TouchableOpacity>
                    </View>
                  ) : (
                    <Text style={styles.noKey}>لم يُضف مفتاح بعد</Text>
                  )}

                  <View style={styles.actionRow}>
                    <TouchableOpacity style={styles.actionBtn} onPress={() => startEditing(provider)}>
                      <Ionicons name={keyConfig ? 'pencil-outline' : 'add-outline'} size={15} color={Colors.text} />
                      <Text style={styles.actionBtnText}>{keyConfig ? 'تعديل' : 'إضافة'}</Text>
                    </TouchableOpacity>

                    {keyConfig && (
                      <>
                        <TouchableOpacity
                          style={styles.actionBtn}
                          onPress={() => handleTest(provider)}
                          disabled={testing === provider}
                        >
                          {testing === provider ? (
                            <ActivityIndicator size="small" color={Colors.accent} />
                          ) : (
                            <Ionicons name="checkmark-circle-outline" size={15} color={Colors.text} />
                          )}
                          <Text style={styles.actionBtnText}>اختبار</Text>
                        </TouchableOpacity>

                        <TouchableOpacity
                          style={[styles.actionBtn, styles.deleteBtn]}
                          onPress={() => handleDelete(provider)}
                        >
                          <Ionicons name="trash-outline" size={15} color={Colors.danger} />
                          <Text style={[styles.actionBtnText, { color: Colors.danger }]}>حذف</Text>
                        </TouchableOpacity>
                      </>
                    )}
                  </View>
                </>
              )}

              {isEditing && (
                <View style={styles.editForm}>
                  <Text style={styles.inputLabel}>API Key</Text>
                  <TextInput
                    style={styles.input}
                    value={apiKeyInput}
                    onChangeText={setApiKeyInput}
                    placeholder="أدخل API Key هنا"
                    placeholderTextColor={Colors.textMuted}
                    secureTextEntry
                    autoCapitalize="none"
                    autoCorrect={false}
                  />

                  {provider === 'custom' && (
                    <>
                      <Text style={styles.inputLabel}>Base URL</Text>
                      <TextInput
                        style={styles.input}
                        value={baseUrlInput}
                        onChangeText={setBaseUrlInput}
                        placeholder="https://your-api.com/v1"
                        placeholderTextColor={Colors.textMuted}
                        autoCapitalize="none"
                        autoCorrect={false}
                        keyboardType="url"
                      />
                      <Text style={styles.inputLabel}>اسم النموذج</Text>
                      <TextInput
                        style={styles.input}
                        value={modelNameInput}
                        onChangeText={setModelNameInput}
                        placeholder="gpt-4o / claude-3-5-sonnet / ..."
                        placeholderTextColor={Colors.textMuted}
                        autoCapitalize="none"
                        autoCorrect={false}
                      />
                    </>
                  )}

                  <View style={styles.editActions}>
                    <TouchableOpacity
                      style={[styles.editBtn, styles.saveBtn]}
                      onPress={() => handleSave(provider)}
                    >
                      <Text style={styles.saveBtnText}>حفظ</Text>
                    </TouchableOpacity>
                    <TouchableOpacity style={styles.editBtn} onPress={cancelEditing}>
                      <Text style={styles.cancelBtnText}>إلغاء</Text>
                    </TouchableOpacity>
                  </View>
                </View>
              )}
            </View>
          );
        })}

        <View style={styles.bottomNote}>
          <Ionicons name="shield-checkmark-outline" size={14} color={Colors.textMuted} />
          <Text style={styles.noteText}>يتم تخزين المفاتيح بشكل آمن على جهازك</Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: Colors.background },
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
  content: { padding: Spacing.md, gap: Spacing.md, paddingBottom: 40 },
  card: {
    backgroundColor: Colors.card,
    borderRadius: BorderRadius.lg,
    padding: Spacing.lg,
    borderWidth: 1,
    borderColor: Colors.border,
    gap: Spacing.md,
  },
  cardActive: { borderColor: Colors.accent + '66' },
  cardHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  cardLeft: { flexDirection: 'row', alignItems: 'center', gap: Spacing.sm },
  cardRight: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  providerName: { fontSize: FontSize.md, fontWeight: FontWeight.semibold, color: Colors.text },
  activeBadge: {
    backgroundColor: Colors.accent + '33',
    paddingHorizontal: Spacing.sm,
    paddingVertical: 2,
    borderRadius: BorderRadius.full,
    borderWidth: 1,
    borderColor: Colors.accent + '66',
  },
  activeBadgeText: { fontSize: FontSize.xs, color: Colors.accent },
  connectedDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: Colors.success },
  connectedText: { fontSize: FontSize.xs, color: Colors.success },
  keyPreview: { flexDirection: 'row', alignItems: 'center', gap: Spacing.sm },
  keyMask: { fontSize: FontSize.sm, color: Colors.textSecondary, fontFamily: 'monospace', flex: 1 },
  noKey: { fontSize: FontSize.sm, color: Colors.textMuted, fontStyle: 'italic' },
  actionRow: { flexDirection: 'row', gap: Spacing.sm, flexWrap: 'wrap' },
  actionBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.xs,
    borderRadius: BorderRadius.sm,
    borderWidth: 1,
    borderColor: Colors.border,
    backgroundColor: Colors.cardAlt,
  },
  actionBtnText: { fontSize: FontSize.sm, color: Colors.text },
  deleteBtn: { borderColor: Colors.danger + '44' },
  editForm: { gap: Spacing.sm },
  inputLabel: { fontSize: FontSize.sm, color: Colors.textSecondary },
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
  editActions: { flexDirection: 'row', gap: Spacing.sm, marginTop: Spacing.sm },
  editBtn: {
    flex: 1,
    paddingVertical: Spacing.md,
    borderRadius: BorderRadius.md,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.border,
  },
  saveBtn: { backgroundColor: Colors.accent + '33', borderColor: Colors.accent },
  saveBtnText: { fontSize: FontSize.md, color: Colors.text, fontWeight: FontWeight.semibold },
  cancelBtnText: { fontSize: FontSize.md, color: Colors.textSecondary },
  bottomNote: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    justifyContent: 'center',
    marginTop: Spacing.md,
  },
  noteText: { fontSize: FontSize.xs, color: Colors.textMuted },
});
