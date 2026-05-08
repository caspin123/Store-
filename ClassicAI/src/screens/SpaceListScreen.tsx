import React, { useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  SafeAreaView,
  Alert,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { useApp } from '../providers/AppProvider';
import { Space } from '../types';
import { Colors, Spacing, FontSize, FontWeight, BorderRadius } from '../constants/theme';
import { formatDate } from '../utils/helpers';

export function SpaceListScreen() {
  const navigation = useNavigation<any>();
  const { state, deleteSpace } = useApp();

  const handleOpen = useCallback((space: Space) => {
    navigation.navigate('SpaceDetails', { spaceId: space.id });
  }, [navigation]);

  const handleDelete = useCallback((space: Space) => {
    Alert.alert(
      'Delete Space',
      `Are you sure you want to delete "${space.name}"? All chats inside will be lost.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: () => deleteSpace(space.id),
        },
      ],
    );
  }, [deleteSpace]);

  const renderSpace = useCallback(({ item }: { item: Space }) => (
    <TouchableOpacity
      style={styles.card}
      onPress={() => handleOpen(item)}
      onLongPress={() => handleDelete(item)}
    >
      <View style={styles.cardIcon}>
        <Ionicons name="layers-outline" size={24} color={Colors.accent} />
      </View>
      <View style={styles.cardInfo}>
        <Text style={styles.cardName} numberOfLines={1}>{item.name}</Text>
        <Text style={styles.cardMeta}>
          {item.chats.length} chat{item.chats.length !== 1 ? 's' : ''} · {item.files.length} file{item.files.length !== 1 ? 's' : ''} · {formatDate(item.updatedAt)}
        </Text>
        {item.instructions ? (
          <Text style={styles.cardInstructions} numberOfLines={2}>{item.instructions}</Text>
        ) : null}
      </View>
      <Ionicons name="chevron-forward" size={16} color={Colors.textMuted} />
    </TouchableOpacity>
  ), [handleOpen, handleDelete]);

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Ionicons name="chevron-back" size={24} color={Colors.text} />
        </TouchableOpacity>
        <Text style={styles.title}>Spaces</Text>
        <TouchableOpacity
          onPress={() => navigation.navigate('CreateSpace')}
          style={styles.addBtn}
        >
          <Ionicons name="add" size={24} color={Colors.text} />
        </TouchableOpacity>
      </View>

      {state.spaces.length === 0 ? (
        <EmptyState onCreate={() => navigation.navigate('CreateSpace')} />
      ) : (
        <FlatList
          data={state.spaces}
          keyExtractor={item => item.id}
          renderItem={renderSpace}
          contentContainerStyle={styles.list}
          showsVerticalScrollIndicator={false}
        />
      )}
    </SafeAreaView>
  );
}

function EmptyState({ onCreate }: { onCreate: () => void }) {
  return (
    <View style={emptyStyles.container}>
      <Ionicons name="layers-outline" size={56} color={Colors.textMuted} />
      <Text style={emptyStyles.title}>No Spaces Yet</Text>
      <Text style={emptyStyles.sub}>
        Create a Space to organize chats around a topic, project, or persona.
      </Text>
      <TouchableOpacity style={emptyStyles.btn} onPress={onCreate}>
        <Ionicons name="add" size={18} color={Colors.text} />
        <Text style={emptyStyles.btnText}>Create Space</Text>
      </TouchableOpacity>
    </View>
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
  addBtn: {
    padding: Spacing.xs,
    width: 40,
    alignItems: 'flex-end',
  },
  list: {
    padding: Spacing.lg,
    gap: Spacing.sm,
  },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.md,
    backgroundColor: Colors.card,
    borderRadius: BorderRadius.md,
    padding: Spacing.md,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  cardIcon: {
    width: 44,
    height: 44,
    borderRadius: BorderRadius.md,
    backgroundColor: Colors.cardAlt,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cardInfo: {
    flex: 1,
    gap: 2,
  },
  cardName: {
    color: Colors.text,
    fontSize: FontSize.md,
    fontWeight: FontWeight.medium,
  },
  cardMeta: {
    color: Colors.textMuted,
    fontSize: FontSize.xs,
  },
  cardInstructions: {
    color: Colors.textSecondary,
    fontSize: FontSize.xs,
    marginTop: 2,
    lineHeight: 16,
  },
});

const emptyStyles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.xxl,
    gap: Spacing.md,
  },
  title: {
    color: Colors.text,
    fontSize: FontSize.xl,
    fontWeight: FontWeight.semibold,
  },
  sub: {
    color: Colors.textSecondary,
    fontSize: FontSize.md,
    textAlign: 'center',
    lineHeight: 22,
  },
  btn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    backgroundColor: Colors.accent,
    paddingHorizontal: Spacing.xl,
    paddingVertical: Spacing.md,
    borderRadius: BorderRadius.md,
    marginTop: Spacing.sm,
  },
  btnText: {
    color: Colors.text,
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
  },
});
