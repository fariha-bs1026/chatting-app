export function isMessageDeleted(message) {
  return Boolean(message.deletedForEveryone || message.expired);
}

export function isMessageExpired(message, now = Date.now()) {
  if (!message.expiresAt || isMessageDeleted(message)) {
    return false;
  }
  const expiresAt = new Date(message.expiresAt).getTime();
  return Number.isFinite(expiresAt) && expiresAt <= now;
}

export function expiredMessage(message) {
  return {
    ...message,
    content: '',
    assetUrl: null,
    assetKey: null,
    type: 'TEXT',
    deletedForEveryone: true,
    expired: true,
    deletedAt: message.expiresAt || message.deletedAt
  };
}
