import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  SafeAreaView,
  Switch,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { useSettings } from '../hooks/useSettings';
import { Colors, Spacing, FontSize, FontWeight, BorderRadius } from '../constants/theme';
import { PROVIDER_NAMES, PROVIDER_MODELS } from '../constants/providers';
import { ProviderID } from '../types';

export function SettingsScreen() {
  const navigation = useNavigation<any>();
  const {
    settings,
    toggleThinking,
    toggleSearch,
    setThinkingMode,
    setSearchMode,
    setProvider,
    setModel,
    setTemperature,
    setMaxTokens,
    availableModels,
  } = useSettings();

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Ionicons name="chevron-back" size={24} color={Colors.text} />
        </TouchableOpacity>
        <Text style={styles.title}>Settings</Text>
        <View style={{ width: 40 }} />
      </View>

      <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>
        {/* AI Provider */}
        <SectionTitle title="AI Provider" />
        <View style={styles.card}>
          <Text style={styles.label}>Provider</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            <View style={styles.chipRow}>
              {(['openrouter', 'claude', 'deepseek', 'gemini', 'custom'] as ProviderID[]).map(p => (
                <TouchableOpacity
                  key={p}
                  style={[styles.chip, settings.provider === p && styles.chipActive]}
                  onPress={() => setProvider(p)}
                >
                  <Text style={[styles.chipText, settings.provider === p && styles.chipTextActive]}>
                    {PROVIDER_NAMES[p]}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          </ScrollView>
        </View>

        <View style={styles.card}>
          <Text style={styles.label}>Model</Text>
          {availableModels.map(m => (
            <TouchableOpacity
              key={m.id}
              style={[styles.modelRow, settings.model === m.id && styles.modelRowActive]}
              onPress={() => setModel(m.id)}
            >
              <View style={styles.modelInfo}>
                <Text style={[styles.modelName, settings.model === m.id && styles.modelNameActive]}>
                  {m.name}
                </Text>
                {m.contextLength && (
                  <Text style={styles.modelMeta}>
                    {(m.contextLength / 1000).toFixed(0)}k ctx
                  </Text>
                )}
              </View>
              <View style={styles.modelBadges}>
                {m.supportsThinking && (
                  <View style={styles.badge}>
                    <Text style={styles.badgeText}>Think</Text>
                  </View>
                )}
                {settings.model === m.id && (
                  <Ionicons name="checkmark-circle" size={18} color={Colors.accent} />
                )}
              </View>
            </TouchableOpacity>
          ))}
        </View>

        {/* Generation */}
        <SectionTitle title="Generation" />
        <View style={styles.card}>
          <Text style={styles.label}>Temperature: {settings.temperature.toFixed(1)}</Text>
          <Text style={styles.hint}>Controls randomness. Lower = more focused, Higher = more creative.</Text>
          <View style={styles.tempRow}>
            {[0.0, 0.3, 0.5, 0.7, 1.0, 1.5].map(v => (
              <TouchableOpacity
                key={v}
                style={[styles.tempBtn, Math.abs(settings.temperature - v) < 0.05 && styles.tempBtnActive]}
                onPress={() => setTemperature(v)}
              >
                <Text style={[styles.tempBtnText, Math.abs(settings.temperature - v) < 0.05 && styles.tempBtnTextActive]}>
                  {v.toFixed(1)}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        <View style={styles.card}>
          <Text style={styles.label}>Max Tokens: {settings.maxTokens}</Text>
          <Text style={styles.hint}>Maximum length of the AI's response.</Text>
          <View style={styles.tempRow}>
            {[512, 1024, 2048, 4096, 8192].map(v => (
              <TouchableOpacity
                key={v}
                style={[styles.tempBtn, settings.maxTokens === v && styles.tempBtnActive]}
                onPress={() => setMaxTokens(v)}
              >
                <Text style={[styles.tempBtnText, settings.maxTokens === v && styles.tempBtnTextActive]}>
                  {v >= 1024 ? `${v / 1024}k` : `${v}`}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Features */}
        <SectionTitle title="Features" />
        <View style={styles.card}>
          <ToggleRow
            icon="bulb-outline"
            label="Extended Thinking"
            description="Enable chain-of-thought reasoning (Claude & DeepSeek models)"
            value={settings.thinkingEnabled}
            onToggle={toggleThinking}
          />
          {settings.thinkingEnabled && (
            <View style={styles.subSection}>
              <Text style={styles.subLabel}>Thinking Mode</Text>
              <View style={styles.modeRow}>
                <ModeBtn
                  label="Direct"
                  active={settings.thinkingMode === 'direct'}
                  onPress={() => setThinkingMode('direct')}
                />
                <ModeBtn
                  label="Extended"
                  active={settings.thinkingMode === 'extended'}
                  onPress={() => setThinkingMode('extended')}
                />
              </View>
            </View>
          )}
        </View>

        <View style={styles.card}>
          <ToggleRow
            icon="search-outline"
            label="Web Search"
            description="Automatically search the web for current information"
            value={settings.searchEnabled}
            onToggle={toggleSearch}
          />
          {settings.searchEnabled && (
            <View style={styles.subSection}>
              <Text style={styles.subLabel}>Search Mode</Text>
              <View style={styles.modeRow}>
                <ModeBtn
                  label="Standard"
                  active={settings.searchMode === 'standard'}
                  onPress={() => setSearchMode('standard')}
                />
                <ModeBtn
                  label="Extended"
                  active={settings.searchMode === 'extended'}
                  onPress={() => setSearchMode('extended')}
                />
              </View>
            </View>
          )}
        </View>

        {/* API Keys */}
        <SectionTitle title="API Keys" />
        <TouchableOpacity
          style={[styles.card, styles.navCard]}
          onPress={() => navigation.navigate('ApiKeys')}
        >
          <Ionicons name="key-outline" size={18} color={Colors.accent} />
          <Text style={styles.navCardText}>Manage API Keys</Text>
          <Ionicons name="chevron-forward" size={16} color={Colors.textMuted} />
        </TouchableOpacity>

        <View style={{ height: Spacing.xxl }} />
      </ScrollView>
    </SafeAreaView>
  );
}

function SectionTitle({ title }: { title: string }) {
  return <Text style={styles.sectionTitle}>{title}</Text>;
}

function ToggleRow({
  icon,
  label,
  description,
  value,
  onToggle,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  label: string;
  description: string;
  value: boolean;
  onToggle: () => void;
}) {
  return (
    <View style={styles.toggleRow}>
      <Ionicons name={icon} size={18} color={Colors.accent} />
      <View style={styles.toggleInfo}>
        <Text style={styles.toggleLabel}>{label}</Text>
        <Text style={styles.toggleDesc}>{description}</Text>
      </View>
      <Switch
        value={value}
        onValueChange={onToggle}
        thumbColor={value ? Colors.text : Colors.textMuted}
        trackColor={{ false: Colors.border, true: Colors.accent }}
      />
    </View>
  );
}

function ModeBtn({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  return (
    <TouchableOpacity
      style={[styles.modeBtn, active && styles.modeBtnActive]}
      onPress={onPress}
    >
      <Text style={[styles.modeBtnText, active && styles.modeBtnTextActive]}>{label}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: Colors.background,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
    borderBottomWidth: 1,
    borderBottomColor: Colors.border,
  },
  backBtn: {
    padding: Spacing.xs,
    width: 40,
  },
  title: {
    flex: 1,
    color: Colors.text,
    fontSize: FontSize.lg,
    fontWeight: FontWeight.semibold,
    textAlign: 'center',
  },
  scroll: {
    flex: 1,
  },
  content: {
    padding: Spacing.lg,
    gap: Spacing.sm,
  },
  sectionTitle: {
    color: Colors.textMuted,
    fontSize: FontSize.xs,
    fontWeight: FontWeight.semibold,
    textTransform: 'uppercase',
    letterSpacing: 0.8,
    marginTop: Spacing.md,
    marginBottom: Spacing.xs,
    marginLeft: Spacing.xs,
  },
  card: {
    backgroundColor: Colors.card,
    borderRadius: BorderRadius.md,
    padding: Spacing.md,
    borderWidth: 1,
    borderColor: Colors.border,
    gap: Spacing.sm,
  },
  label: {
    color: Colors.text,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.medium,
  },
  hint: {
    color: Colors.textMuted,
    fontSize: FontSize.xs,
    lineHeight: 16,
  },
  chipRow: {
    flexDirection: 'row',
    gap: Spacing.sm,
    paddingVertical: Spacing.xs,
  },
  chip: {
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.xs,
    borderRadius: BorderRadius.full,
    backgroundColor: Colors.cardAlt,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  chipActive: {
    backgroundColor: Colors.accent,
    borderColor: Colors.accent,
  },
  chipText: {
    color: Colors.textSecondary,
    fontSize: FontSize.sm,
  },
  chipTextActive: {
    color: Colors.text,
    fontWeight: FontWeight.medium,
  },
  modelRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: Spacing.sm,
    paddingHorizontal: Spacing.sm,
    borderRadius: BorderRadius.sm,
    gap: Spacing.sm,
  },
  modelRowActive: {
    backgroundColor: Colors.cardAlt,
  },
  modelInfo: {
    flex: 1,
  },
  modelName: {
    color: Colors.textSecondary,
    fontSize: FontSize.sm,
  },
  modelNameActive: {
    color: Colors.text,
    fontWeight: FontWeight.medium,
  },
  modelMeta: {
    color: Colors.textMuted,
    fontSize: FontSize.xs,
    marginTop: 1,
  },
  modelBadges: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs,
  },
  badge: {
    backgroundColor: Colors.background,
    borderRadius: 4,
    paddingHorizontal: 4,
    paddingVertical: 1,
    borderWidth: 1,
    borderColor: Colors.accent,
  },
  badgeText: {
    color: Colors.accent,
    fontSize: 9,
  },
  tempRow: {
    flexDirection: 'row',
    gap: Spacing.xs,
    flexWrap: 'wrap',
  },
  tempBtn: {
    paddingHorizontal: Spacing.sm,
    paddingVertical: Spacing.xs,
    borderRadius: BorderRadius.sm,
    backgroundColor: Colors.cardAlt,
    minWidth: 44,
    alignItems: 'center',
  },
  tempBtnActive: {
    backgroundColor: Colors.accent,
  },
  tempBtnText: {
    color: Colors.textSecondary,
    fontSize: FontSize.xs,
  },
  tempBtnTextActive: {
    color: Colors.text,
    fontWeight: FontWeight.medium,
  },
  toggleRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.md,
  },
  toggleInfo: {
    flex: 1,
    gap: 2,
  },
  toggleLabel: {
    color: Colors.text,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.medium,
  },
  toggleDesc: {
    color: Colors.textMuted,
    fontSize: FontSize.xs,
    lineHeight: 16,
  },
  subSection: {
    paddingLeft: Spacing.xl + Spacing.sm,
    gap: Spacing.xs,
  },
  subLabel: {
    color: Colors.textMuted,
    fontSize: FontSize.xs,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  modeRow: {
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  modeBtn: {
    flex: 1,
    paddingVertical: Spacing.xs,
    borderRadius: BorderRadius.sm,
    backgroundColor: Colors.cardAlt,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.border,
  },
  modeBtnActive: {
    backgroundColor: Colors.accent,
    borderColor: Colors.accent,
  },
  modeBtnText: {
    color: Colors.textSecondary,
    fontSize: FontSize.xs,
  },
  modeBtnTextActive: {
    color: Colors.text,
    fontWeight: FontWeight.medium,
  },
  navCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.md,
  },
  navCardText: {
    flex: 1,
    color: Colors.text,
    fontSize: FontSize.sm,
    fontWeight: FontWeight.medium,
  },
});
