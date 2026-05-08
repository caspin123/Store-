import React, { useEffect, useRef, useState, useCallback, createContext, useContext } from 'react';
import {
  Animated,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { Colors, FontSize, BorderRadius, Spacing } from '../constants/theme';

export type ToastType = 'success' | 'error' | 'info';

export interface ToastMessage {
  id: string;
  message: string;
  type: ToastType;
  duration?: number;
}

interface ToastItemProps {
  toast: ToastMessage;
  onDismiss: (id: string) => void;
}

function ToastItem({ toast, onDismiss }: ToastItemProps) {
  const opacity = useRef(new Animated.Value(0)).current;
  const translateY = useRef(new Animated.Value(-20)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(opacity, { toValue: 1, duration: 250, useNativeDriver: true }),
      Animated.timing(translateY, { toValue: 0, duration: 250, useNativeDriver: true }),
    ]).start();

    const timer = setTimeout(() => {
      Animated.parallel([
        Animated.timing(opacity, { toValue: 0, duration: 250, useNativeDriver: true }),
        Animated.timing(translateY, { toValue: -20, duration: 250, useNativeDriver: true }),
      ]).start(() => onDismiss(toast.id));
    }, toast.duration ?? 3000);

    return () => clearTimeout(timer);
  }, []);

  const bgColor =
    toast.type === 'success'
      ? Colors.success
      : toast.type === 'error'
      ? Colors.danger
      : Colors.card;

  return (
    <Animated.View style={[styles.toastItem, { backgroundColor: bgColor, opacity, transform: [{ translateY }] }]}>
      <Text style={styles.toastText}>{toast.message}</Text>
      <TouchableOpacity onPress={() => onDismiss(toast.id)} style={styles.dismiss}>
        <Text style={styles.dismissText}>✕</Text>
      </TouchableOpacity>
    </Animated.View>
  );
}

interface ToastContainerProps {
  toasts: ToastMessage[];
  onDismiss: (id: string) => void;
}

export function ToastContainer({ toasts, onDismiss }: ToastContainerProps) {
  if (!toasts.length) return null;
  return (
    <View style={styles.container} pointerEvents="box-none">
      {toasts.map(t => (
        <ToastItem key={t.id} toast={t} onDismiss={onDismiss} />
      ))}
    </View>
  );
}

// ── Standalone hook to manage toasts ─────────────────────────────────────────

let _counter = 0;

export function useToast() {
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const show = useCallback((message: string, type: ToastType = 'info', duration = 3000) => {
    const id = `toast_${++_counter}`;
    setToasts(prev => [...prev, { id, message, type, duration }]);
  }, []);

  const dismiss = useCallback((id: string) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  }, []);

  return { toasts, show, dismiss };
}

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    top: 60,
    left: Spacing.lg,
    right: Spacing.lg,
    zIndex: 9999,
    gap: Spacing.sm,
  },
  toastItem: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: BorderRadius.md,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
    shadowColor: '#000',
    shadowOpacity: 0.3,
    shadowOffset: { width: 0, height: 2 },
    shadowRadius: 4,
    elevation: 6,
  },
  toastText: {
    flex: 1,
    color: Colors.text,
    fontSize: FontSize.sm,
  },
  dismiss: {
    marginLeft: Spacing.sm,
    padding: 2,
  },
  dismissText: {
    color: Colors.textSecondary,
    fontSize: FontSize.sm,
  },
});
