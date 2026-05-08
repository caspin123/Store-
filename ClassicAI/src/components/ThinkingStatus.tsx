import React, { useEffect, useRef } from 'react';
import { View, Text, StyleSheet, Animated } from 'react-native';
import { ThinkingStatus as TStatus } from '../types';
import { Colors, FontSize, Spacing, BorderRadius } from '../constants/theme';

const STATUS_LABELS: Record<TStatus, string> = {
  idle: '',
  thinking: 'جارٍ التفكير...',
  understanding: 'فهم الطلب...',
  searching: 'جارٍ البحث...',
  searching_deep: 'بحث عميق...',
  reviewing: 'مراجعة المصادر...',
  preparing: 'إعداد الإجابة...',
  generating: 'جارٍ التوليد...',
};

interface Props {
  status: TStatus;
}

export function ThinkingStatus({ status }: Props) {
  const dot1 = useRef(new Animated.Value(0.3)).current;
  const dot2 = useRef(new Animated.Value(0.3)).current;
  const dot3 = useRef(new Animated.Value(0.3)).current;

  useEffect(() => {
    if (status === 'idle') return;
    const animate = (dot: Animated.Value, delay: number) =>
      Animated.loop(
        Animated.sequence([
          Animated.delay(delay),
          Animated.timing(dot, { toValue: 1, duration: 400, useNativeDriver: true }),
          Animated.timing(dot, { toValue: 0.3, duration: 400, useNativeDriver: true }),
        ]),
      );
    const a1 = animate(dot1, 0);
    const a2 = animate(dot2, 150);
    const a3 = animate(dot3, 300);
    a1.start(); a2.start(); a3.start();
    return () => { a1.stop(); a2.stop(); a3.stop(); };
  }, [status]);

  if (status === 'idle') return null;

  return (
    <View style={styles.container}>
      <View style={styles.dotsRow}>
        <Animated.View style={[styles.dot, { opacity: dot1 }]} />
        <Animated.View style={[styles.dot, { opacity: dot2 }]} />
        <Animated.View style={[styles.dot, { opacity: dot3 }]} />
      </View>
      <Text style={styles.label}>{STATUS_LABELS[status]}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.sm,
    gap: Spacing.sm,
  },
  dotsRow: {
    flexDirection: 'row',
    gap: 4,
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: BorderRadius.full,
    backgroundColor: Colors.accent,
  },
  label: {
    fontSize: FontSize.sm,
    color: Colors.textSecondary,
    fontStyle: 'italic',
  },
});
