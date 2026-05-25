import { describe, expect, it } from 'vitest';
import { fallbackErrorMessage, messageFromError } from './errors';

describe('messageFromError', () => {
  it('uses explicit error messages first', () => {
    expect(messageFromError(new Error('Invalid file'))).toBe('Invalid file');
  });

  it('falls back when no error message exists', () => {
    expect(messageFromError(null)).toBe(fallbackErrorMessage);
    expect(messageFromError({}, 'Try again')).toBe('Try again');
  });
});
