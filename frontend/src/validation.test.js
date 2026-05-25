import { afterEach, describe, expect, it } from 'vitest';
import { setStoredLanguage } from './language';
import {
  MAX_IMAGE_BYTES,
  MAX_MEDIA_BYTES,
  messageTypeForFile,
  normalizePhoneNumber,
  validateImageFile,
  validateMediaFile,
  validateProfileForm
} from './validation';

function file(type, size = 1024) {
  return { type, size };
}

afterEach(() => {
  setStoredLanguage('en');
});

describe('validation', () => {
  it('normalizes phone numbers without changing E.164 format', () => {
    expect(normalizePhoneNumber(' +880 1712345678 ')).toBe('+8801712345678');
  });

  it('validates profile display name and phone number centrally', () => {
    expect(validateProfileForm({ displayName: 'A', phoneNumber: '+8801712345678' }))
      .toBe('Display name must be 2-120 characters.');
    expect(validateProfileForm({ displayName: 'Alice', phoneNumber: '01712345678' }))
      .toBe('Phone number must look like +8801712345678.');
    expect(validateProfileForm({ displayName: 'Alice', phoneNumber: '+8801712345678' })).toBeNull();
  });

  it('rejects unsupported or oversized profile images', () => {
    expect(validateImageFile(file('application/pdf'))).toBe('Choose a JPG, PNG, GIF, or WebP image.');
    expect(validateImageFile(file('image/png', MAX_IMAGE_BYTES + 1))).toBe('Image must be 10 MB or smaller.');
    expect(validateImageFile(file('image/webp'))).toBeNull();
  });

  it('rejects unsupported or oversized media files with localized messages', () => {
    expect(validateMediaFile(file('application/pdf'))).toBe('Choose a supported image, audio, or video file.');
    expect(validateMediaFile(file('video/mp4', MAX_MEDIA_BYTES + 1))).toBe('Media file must be 50 MB or smaller.');

    setStoredLanguage('bn');
    expect(validateMediaFile(file('application/pdf'))).toContain('সমর্থিত');
  });

  it('maps file MIME types to message types', () => {
    expect(messageTypeForFile(file('image/png'))).toBe('IMAGE');
    expect(messageTypeForFile(file('audio/mpeg'))).toBe('AUDIO');
    expect(messageTypeForFile(file('video/mp4'))).toBe('VIDEO');
    expect(messageTypeForFile(file('application/pdf'))).toBe('FILE');
    expect(messageTypeForFile(null)).toBe('FILE');
  });
});
