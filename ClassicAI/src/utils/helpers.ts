import { Chat, Message } from '../types';

export function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2);
}

export function formatDate(timestamp: number): string {
  const date = new Date(timestamp);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));

  if (days === 0) {
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  } else if (days === 1) {
    return 'Yesterday';
  } else if (days < 7) {
    return date.toLocaleDateString([], { weekday: 'long' });
  } else {
    return date.toLocaleDateString([], { month: 'short', day: 'numeric' });
  }
}

export function truncateText(text: string, maxLength: number): string {
  if (text.length <= maxLength) return text;
  return text.slice(0, maxLength) + '...';
}

export function extractChatTitle(messages: Message[]): string {
  const firstUserMsg = messages.find(m => m.role === 'user');
  if (!firstUserMsg) return 'New Chat';
  return truncateText(firstUserMsg.content, 40);
}

export function groupChatsByDate(chats: Chat[]): { label: string; chats: Chat[] }[] {
  const now = new Date();
  const today: Chat[] = [];
  const yesterday: Chat[] = [];
  const thisWeek: Chat[] = [];
  const older: Chat[] = [];

  for (const chat of chats) {
    const date = new Date(chat.updatedAt);
    const diffDays = Math.floor(
      (now.getTime() - date.getTime()) / (1000 * 60 * 60 * 24),
    );
    if (diffDays === 0) today.push(chat);
    else if (diffDays === 1) yesterday.push(chat);
    else if (diffDays < 7) thisWeek.push(chat);
    else older.push(chat);
  }

  const groups: { label: string; chats: Chat[] }[] = [];
  if (today.length) groups.push({ label: 'Today', chats: today });
  if (yesterday.length) groups.push({ label: 'Yesterday', chats: yesterday });
  if (thisWeek.length) groups.push({ label: 'This Week', chats: thisWeek });
  if (older.length) groups.push({ label: 'Older', chats: older });
  return groups;
}

export function buildSystemPrompt(
  spaceInstructions?: string,
  searchResults?: string,
  files?: Array<{ name: string; content: string }>,
  links?: string[],
): string {
  const parts: string[] = [];

  if (spaceInstructions) {
    parts.push(`Space Instructions:\n${spaceInstructions}`);
  }

  if (files && files.length > 0) {
    const fileContext = files
      .map(f => `--- File: ${f.name} ---\n${f.content.slice(0, 3000)}`)
      .join('\n\n');
    parts.push(`Attached Documents:\n${fileContext}`);
  }

  if (links && links.length > 0) {
    parts.push(`Reference Links:\n${links.join('\n')}`);
  }

  if (searchResults) {
    parts.push(`Search Results:\n${searchResults}`);
  }

  return parts.join('\n\n---\n\n');
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}
