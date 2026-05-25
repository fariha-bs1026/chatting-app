import { describe, expect, it } from 'vitest';
import { translate } from './i18n';

describe('translate', () => {
  it('returns translated values with placeholders', () => {
    expect(translate('en', 'chat.unread', { count: 3 })).toBe('3 unread messages');
  });

  it('falls back to English and then the key', () => {
    expect(translate('fr', 'message.send')).toBe('Send');
    expect(translate('en', 'missing.key')).toBe('missing.key');
  });
});
