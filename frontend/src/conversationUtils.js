import { translate } from './i18n';

export function initials(name) {
  return (name || '?')
    .split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
}

export function formatTime(value) {
  if (!value) {
    return '';
  }
  return new Intl.DateTimeFormat(undefined, {
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value));
}

export function directParticipant(conversation, currentUser) {
  if (!conversation?.direct || !currentUser) {
    return null;
  }
  return conversation.participants.find((user) => user.id !== currentUser.id) || null;
}

export function conversationTitle(conversation, currentUser, language = 'en') {
  if (!conversation) {
    return '';
  }
  if (!conversation.direct) {
    return conversation.name || translate(language, 'conversation.group');
  }
  const other = directParticipant(conversation, currentUser);
  return other?.displayName || translate(language, 'conversation.direct');
}

export function conversationSubtitle(conversation, currentUser, language = 'en') {
  if (!conversation) {
    return '';
  }
  if (!conversation.direct) {
    return translate(language, 'conversation.members', { count: conversation.participants.length });
  }
  const other = directParticipant(conversation, currentUser);
  if (!other) {
    return '';
  }
  return other.online
    ? translate(language, 'conversation.online')
    : other.lastSeenAt
      ? translate(language, 'conversation.lastSeen', { time: formatTime(other.lastSeenAt) })
      : translate(language, 'conversation.offline');
}
