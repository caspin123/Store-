import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Switch,
  SafeAreaView,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { useApp } from '../providers/AppProvider';
import { Colors, FontSize, Spacing, BorderRadius, FontWeight } from '../constants/theme';
import { ProviderID } from '../types';
import { PROVIDER_NAMES } from '../constants/providers';

export function SidebarDrawer(props: any) {
  const navigation = useNavigation<any>();
  const { state, startNewChat, updateSettings, toggleThinking, toggleSearch, setCurrentSpace } = useApp();
  const { settings, chats, spaces } = state;

  const [expandedSection, setExpandedSection] = useState<string | null>(null);

  const toggleSection = (key: string) => {
    setExpandedSection(prev => (prev === key ? null : key));
  };

  const rootNav = navigation.getParent() ?? navigation;

  const handleNewChat = () => {
    startNewChat();
    navigation.closeDrawer();
  };

  const handleOpenSpace = (spaceId: string) => {
    setCurrentSpace(spaceId);
    navigation.closeDrawer();
    rootNav.navigate('SpaceChat', { spaceId });
  };

  const handleCreateSpace = () => {
    navigation.closeDrawer();
    rootNav.navigate('CreateSpace');
  };

  const handleOpenSettings = () => {
    navigation.closeDrawer();
    rootNav.navigate('Settings');
  };

  const handleOpenApiKeys = () => {
    navigation.closeDrawer();
    rootNav.navigate('ApiKeys');
  };

  const handleSelectChat = (chatId: string) => {
    navigation.closeDrawer();
    rootNav.navigate('Chat', { chatId });
  };

  const SectionHeader = ({ label, sectionKey, icon }: { label: string; sectionKey: string; icon: string }) => (
    <TouchableOpacity
      style={styles.sectionHeader}
      onPress={() => toggleSection(sectionKey)}
    >
      <View style={styles.sectionHeaderLeft}>
        <Ionicons name={icon as any} size={16} color={Colors.accent} />
        <Text style={styles.sectionLabel}>{label}</Text>
      </View>
      <Ionicons
        name={expandedSection === sectionKey ? 'chevron-up' : 'chevron-down'}
        size={14}
        color={Colors.textMuted}
      />
    </TouchableOpacity>
  );

  const recentChats = chats.slice(0, 10);

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.brandRow}>
        <Text style={styles.brand}>Classic AI</Text>
        <TouchableOpacity onPress={handleOpenSettings} style={styles.settingsBtn}>
          <Ionicons name="settings-outline" size={20} color={Colors.textSecondary} />
        </TouchableOpacity>
      </View>

      <TouchableOpacity style={styles.newChatBtn} onPress={handleNewChat}>
        <Ionicons name="add" size={18} color={Colors.text} />
        <Text style={styles.newChatText}>محادثة جديدة</Text>
      </TouchableOpacity>

      <ScrollView style={styles.scroll} showsVerticalScrollIndicator={false}>

        {/* Search */}
        <View style={styles.section}>
          <View style={styles.toggleRow}>
            <View style={styles.toggleLeft}>
              <Ionicons name="search-outline" size={16} color={Colors.accent} />
              <Text style={styles.toggleLabel}>البحث</Text>
            </View>
            <Switch
              value={settings.searchEnabled}
              onValueChange={toggleSearch}
              trackColor={{ false: Colors.border, true: Colors.accent }}
              thumbColor={Colors.text}
            />
          </View>

          {settings.searchEnabled && (
            <View style={styles.subOptions}>
              <Text style={styles.subLabel}>وضع البحث</Text>
              <View style={styles.optionRow}>
                {(['standard', 'extended'] as const).map(mode => (
                  <TouchableOpacity
                    key={mode}
                    style={[styles.optionBtn, settings.searchMode === mode && styles.optionBtnActive]}
                    onPress={() => updateSettings({ searchMode: mode })}
                  >
                    <Text style={[styles.optionText, settings.searchMode === mode && styles.optionTextActive]}>
                      {mode === 'standard' ? 'بحث عادي' : 'بحث موسّع'}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          )}
        </View>

        {/* Thinking */}
        <View style={styles.section}>
          <View style={styles.toggleRow}>
            <View style={styles.toggleLeft}>
              <Ionicons name="bulb-outline" size={16} color={Colors.accent} />
              <Text style={styles.toggleLabel}>التفكير</Text>
            </View>
            <Switch
              value={settings.thinkingEnabled}
              onValueChange={toggleThinking}
              trackColor={{ false: Colors.border, true: Colors.accent }}
              thumbColor={Colors.text}
            />
          </View>

          {settings.thinkingEnabled && (
            <View style={styles.subOptions}>
              <Text style={styles.subLabel}>وضع التفكير</Text>
              <View style={styles.optionRow}>
                {(['direct', 'extended'] as const).map(mode => (
                  <TouchableOpacity
                    key={mode}
                    style={[styles.optionBtn, settings.thinkingMode === mode && styles.optionBtnActive]}
                    onPress={() => updateSettings({ thinkingMode: mode })}
                  >
                    <Text style={[styles.optionText, settings.thinkingMode === mode && styles.optionTextActive]}>
                      {mode === 'direct' ? 'مباشر' : 'موسّع'}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          )}
        </View>

        {/* Model */}
        <View style={styles.section}>
          <SectionHeader label="النموذج" sectionKey="model" icon="cube-outline" />
          {expandedSection === 'model' && (
            <View style={styles.expandedContent}>
              <Text style={styles.subLabel}>مزود API</Text>
              <Text style={styles.currentValue}>{PROVIDER_NAMES[settings.provider]}</Text>
              <Text style={styles.subLabel}>النموذج الحالي</Text>
              <Text style={styles.currentValue}>{settings.model}</Text>
            </View>
          )}
        </View>

        {/* API Settings */}
        <TouchableOpacity style={styles.navItem} onPress={handleOpenApiKeys}>
          <Ionicons name="key-outline" size={16} color={Colors.accent} />
          <Text style={styles.navItemText}>إعدادات API</Text>
          <Ionicons name="chevron-forward" size={14} color={Colors.textMuted} />
        </TouchableOpacity>

        {/* Spaces */}
        <View style={styles.section}>
          <View style={styles.sectionHeaderRow}>
            <View style={styles.toggleLeft}>
              <Ionicons name="folder-outline" size={16} color={Colors.accent} />
              <Text style={styles.sectionLabel}>المساحات</Text>
            </View>
            <TouchableOpacity onPress={handleCreateSpace} style={styles.addBtn}>
              <Ionicons name="add" size={16} color={Colors.accent} />
            </TouchableOpacity>
          </View>

          {spaces.length === 0 ? (
            <Text style={styles.emptyHint}>أنشئ Space جديد للتركيز على موضوع معين</Text>
          ) : (
            spaces.slice(0, 8).map(space => (
              <TouchableOpacity
                key={space.id}
                style={styles.spaceItem}
                onPress={() => handleOpenSpace(space.id)}
              >
                <Ionicons name="folder" size={14} color={Colors.textSecondary} />
                <Text style={styles.spaceItemText} numberOfLines={1}>{space.name}</Text>
              </TouchableOpacity>
            ))
          )}

          <TouchableOpacity style={styles.createSpaceBtn} onPress={handleCreateSpace}>
            <Ionicons name="add-circle-outline" size={14} color={Colors.accent} />
            <Text style={styles.createSpaceText}>إنشاء مساحة</Text>
          </TouchableOpacity>
        </View>

        {/* Recent Chats */}
        {recentChats.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>المحادثات الأخيرة</Text>
            {recentChats.map(chat => (
              <TouchableOpacity
                key={chat.id}
                style={[styles.chatItem, state.currentChatId === chat.id && styles.chatItemActive]}
                onPress={() => handleSelectChat(chat.id)}
              >
                <Ionicons name="chatbubble-outline" size={13} color={Colors.textMuted} />
                <Text style={styles.chatItemText} numberOfLines={1}>{chat.title}</Text>
              </TouchableOpacity>
            ))}
          </View>
        )}

        {/* Agent Soon */}
        <View style={styles.section}>
          <View style={styles.agentRow}>
            <View style={styles.toggleLeft}>
              <Ionicons name="hardware-chip-outline" size={16} color={Colors.textMuted} />
              <Text style={styles.agentLabel}>Agent</Text>
            </View>
            <View style={styles.soonBadge}>
              <Text style={styles.soonText}>قريباً</Text>
            </View>
          </View>
        </View>

      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: Colors.sidebar,
  },
  brandRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: Spacing.lg,
    paddingTop: Spacing.lg,
    paddingBottom: Spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: Colors.border,
  },
  brand: {
    fontSize: FontSize.xl,
    fontWeight: FontWeight.bold,
    color: Colors.text,
    letterSpacing: -0.5,
  },
  settingsBtn: {
    padding: Spacing.xs,
  },
  newChatBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    margin: Spacing.md,
    padding: Spacing.md,
    backgroundColor: Colors.card,
    borderRadius: BorderRadius.md,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  newChatText: {
    fontSize: FontSize.md,
    color: Colors.text,
    fontWeight: FontWeight.medium,
  },
  scroll: {
    flex: 1,
  },
  section: {
    paddingHorizontal: Spacing.md,
    marginBottom: Spacing.sm,
  },
  sectionHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: Spacing.sm,
  },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: Spacing.sm,
  },
  sectionHeaderLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
  },
  sectionLabel: {
    fontSize: FontSize.md,
    color: Colors.text,
    fontWeight: FontWeight.medium,
  },
  sectionTitle: {
    fontSize: FontSize.sm,
    color: Colors.textMuted,
    fontWeight: FontWeight.semibold,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    paddingVertical: Spacing.sm,
  },
  toggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: Spacing.sm,
  },
  toggleLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
  },
  toggleLabel: {
    fontSize: FontSize.md,
    color: Colors.text,
    fontWeight: FontWeight.medium,
  },
  subOptions: {
    paddingLeft: Spacing.xl,
    paddingBottom: Spacing.sm,
    gap: Spacing.sm,
  },
  subLabel: {
    fontSize: FontSize.xs,
    color: Colors.textMuted,
    marginBottom: 4,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  optionRow: {
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  optionBtn: {
    flex: 1,
    paddingVertical: Spacing.xs,
    paddingHorizontal: Spacing.sm,
    borderRadius: BorderRadius.sm,
    borderWidth: 1,
    borderColor: Colors.border,
    alignItems: 'center',
  },
  optionBtnActive: {
    borderColor: Colors.accent,
    backgroundColor: Colors.accent + '22',
  },
  optionText: {
    fontSize: FontSize.xs,
    color: Colors.textSecondary,
  },
  optionTextActive: {
    color: Colors.text,
    fontWeight: FontWeight.medium,
  },
  expandedContent: {
    paddingLeft: Spacing.xl,
    paddingBottom: Spacing.sm,
    gap: 4,
  },
  currentValue: {
    fontSize: FontSize.sm,
    color: Colors.text,
    marginBottom: Spacing.sm,
  },
  navItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.md,
    marginBottom: Spacing.xs,
  },
  navItemText: {
    flex: 1,
    fontSize: FontSize.md,
    color: Colors.text,
    fontWeight: FontWeight.medium,
  },
  spaceItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    paddingVertical: Spacing.sm,
    paddingLeft: Spacing.sm,
  },
  spaceItemText: {
    fontSize: FontSize.sm,
    color: Colors.textSecondary,
    flex: 1,
  },
  createSpaceBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.xs,
    paddingVertical: Spacing.sm,
    paddingLeft: Spacing.sm,
    marginTop: 2,
  },
  createSpaceText: {
    fontSize: FontSize.sm,
    color: Colors.accent,
  },
  addBtn: {
    padding: Spacing.xs,
  },
  emptyHint: {
    fontSize: FontSize.xs,
    color: Colors.textMuted,
    paddingLeft: Spacing.xl,
    paddingBottom: Spacing.sm,
    lineHeight: 18,
  },
  chatItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    paddingVertical: Spacing.sm,
    paddingHorizontal: Spacing.sm,
    borderRadius: BorderRadius.sm,
  },
  chatItemActive: {
    backgroundColor: Colors.card,
  },
  chatItemText: {
    fontSize: FontSize.sm,
    color: Colors.textSecondary,
    flex: 1,
  },
  agentRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: Spacing.sm,
    opacity: 0.5,
  },
  agentLabel: {
    fontSize: FontSize.md,
    color: Colors.textMuted,
  },
  soonBadge: {
    backgroundColor: Colors.border,
    paddingHorizontal: Spacing.sm,
    paddingVertical: 2,
    borderRadius: BorderRadius.full,
  },
  soonText: {
    fontSize: FontSize.xs,
    color: Colors.textMuted,
  },
});
