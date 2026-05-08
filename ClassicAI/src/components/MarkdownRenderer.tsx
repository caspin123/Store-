import React, { useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
} from 'react-native';
import * as Clipboard from 'expo-clipboard';
import Markdown from 'react-native-markdown-display';
import { Colors, FontSize, Spacing, BorderRadius, FontWeight } from '../constants/theme';

interface Props {
  content: string;
  selectable?: boolean;
}

function CodeBlock({ children, language }: { children: string; language?: string }) {
  const handleCopy = useCallback(() => {
    Clipboard.setStringAsync(children);
  }, [children]);

  return (
    <View style={codeStyles.container}>
      <View style={codeStyles.header}>
        <Text style={codeStyles.lang}>{language || 'code'}</Text>
        <TouchableOpacity onPress={handleCopy} style={codeStyles.copyBtn}>
          <Text style={codeStyles.copyText}>Copy</Text>
        </TouchableOpacity>
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false}>
        <Text style={codeStyles.code} selectable>{children}</Text>
      </ScrollView>
    </View>
  );
}

export function MarkdownRenderer({ content, selectable = true }: Props) {
  const rules = {
    fence: (node: any, children: any, parent: any, styles: any) => {
      const code = node.content ?? '';
      const lang = node.sourceInfo ?? '';
      return <CodeBlock key={node.key} children={code.trim()} language={lang} />;
    },
    code_inline: (node: any, children: any, parent: any, styles: any) => (
      <Text key={node.key} style={inlineCodeStyle}>{node.content}</Text>
    ),
  };

  return (
    <Markdown
      rules={rules as any}
      style={markdownStyles}
    >
      {content}
    </Markdown>
  );
}

const inlineCodeStyle = {
  fontFamily: 'monospace' as const,
  fontSize: FontSize.sm,
  backgroundColor: Colors.codeBlock,
  color: '#A8D8A8',
  paddingHorizontal: 4,
  borderRadius: 3,
};

const codeStyles = StyleSheet.create({
  container: {
    backgroundColor: Colors.codeBlock,
    borderRadius: BorderRadius.md,
    borderWidth: 1,
    borderColor: Colors.codeBorder,
    marginVertical: Spacing.sm,
    overflow: 'hidden',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.xs,
    borderBottomWidth: 1,
    borderBottomColor: Colors.codeBorder,
    backgroundColor: '#0E0E0E',
  },
  lang: {
    color: Colors.textMuted,
    fontSize: FontSize.xs,
    fontFamily: 'monospace',
  },
  copyBtn: {
    paddingHorizontal: Spacing.sm,
    paddingVertical: 2,
    borderRadius: BorderRadius.sm,
    backgroundColor: Colors.card,
  },
  copyText: {
    color: Colors.textSecondary,
    fontSize: FontSize.xs,
  },
  code: {
    fontFamily: 'monospace',
    fontSize: FontSize.sm,
    color: '#D4D4D4',
    padding: Spacing.md,
    lineHeight: 20,
  },
});

const markdownStyles = StyleSheet.create({
  body: {
    color: Colors.text,
    fontSize: FontSize.md,
    lineHeight: 24,
  },
  heading1: {
    color: Colors.text,
    fontSize: FontSize.xxl,
    fontWeight: FontWeight.bold,
    marginTop: Spacing.lg,
    marginBottom: Spacing.sm,
  },
  heading2: {
    color: Colors.text,
    fontSize: FontSize.xl,
    fontWeight: FontWeight.semibold,
    marginTop: Spacing.md,
    marginBottom: Spacing.xs,
  },
  heading3: {
    color: Colors.text,
    fontSize: FontSize.lg,
    fontWeight: FontWeight.semibold,
    marginTop: Spacing.sm,
    marginBottom: Spacing.xs,
  },
  paragraph: {
    color: Colors.text,
    fontSize: FontSize.md,
    lineHeight: 24,
    marginBottom: Spacing.sm,
  },
  strong: {
    color: Colors.text,
    fontWeight: FontWeight.bold,
  },
  em: {
    color: Colors.text,
    fontStyle: 'italic',
  },
  bullet_list: {
    marginBottom: Spacing.sm,
  },
  ordered_list: {
    marginBottom: Spacing.sm,
  },
  list_item: {
    flexDirection: 'row',
    marginBottom: 4,
  },
  bullet_list_icon: {
    color: Colors.accent,
    marginRight: Spacing.sm,
  },
  ordered_list_icon: {
    color: Colors.accent,
    marginRight: Spacing.sm,
  },
  blockquote: {
    backgroundColor: Colors.card,
    borderLeftWidth: 3,
    borderLeftColor: Colors.accent,
    paddingLeft: Spacing.md,
    paddingVertical: Spacing.xs,
    marginVertical: Spacing.sm,
    borderRadius: BorderRadius.sm,
  },
  code_block: {
    display: 'none' as any,
  },
  code_inline: {
    display: 'none' as any,
  },
  fence: {
    display: 'none' as any,
  },
  link: {
    color: Colors.accentLight,
    textDecorationLine: 'underline' as const,
  },
  table: {
    borderWidth: 1,
    borderColor: Colors.border,
    borderRadius: BorderRadius.sm,
    marginVertical: Spacing.sm,
    overflow: 'hidden' as const,
  },
  thead: {
    backgroundColor: Colors.card,
  },
  tr: {
    borderBottomWidth: 1,
    borderBottomColor: Colors.border,
    flexDirection: 'row' as const,
  },
  th: {
    flex: 1,
    padding: Spacing.sm,
    color: Colors.text,
    fontWeight: FontWeight.semibold,
    fontSize: FontSize.sm,
  },
  td: {
    flex: 1,
    padding: Spacing.sm,
    color: Colors.textSecondary,
    fontSize: FontSize.sm,
  },
  hr: {
    backgroundColor: Colors.border,
    height: 1,
    marginVertical: Spacing.md,
  },
} as any);
