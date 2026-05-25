import { describe, expect, it } from 'vitest';
import {
  expiredMessage,
  isMessageDeleted,
  isMessageExpired
} from './messageUtils';

describe('message utilities', () => {
  it('detects deleted and expired messages', () => {
    expect(isMessageDeleted({ deletedForEveryone: true })).toBe(true);
    expect(isMessageDeleted({ expired: true })).toBe(true);
    expect(isMessageExpired({ expiresAt: '2026-05-25T00:00:00Z' }, Date.parse('2026-05-25T00:01:00Z')))
      .toBe(true);
    expect(isMessageExpired({ expiresAt: '2026-05-25T00:02:00Z' }, Date.parse('2026-05-25T00:01:00Z')))
      .toBe(false);
  });

  it('creates local tombstones for expired messages', () => {
    const message = {
      content: 'secret',
      assetUrl: 'https://asset',
      assetKey: 'users/a/file.png',
      type: 'IMAGE',
      expiresAt: '2026-05-25T00:00:00Z'
    };

    expect(expiredMessage(message)).toMatchObject({
      content: '',
      assetUrl: null,
      assetKey: null,
      type: 'TEXT',
      deletedForEveryone: true,
      expired: true,
      deletedAt: '2026-05-25T00:00:00Z'
    });
  });

});
