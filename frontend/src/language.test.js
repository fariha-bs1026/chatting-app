import { afterEach, describe, expect, it, vi } from 'vitest';
import { getApiLanguage, getStoredLanguage, setStoredLanguage } from './language';

afterEach(() => {
  localStorage.clear();
  setStoredLanguage('en');
});

describe('language storage', () => {
  it('stores and exposes supported language selections', () => {
    expect(setStoredLanguage('bn')).toBe('bn');
    expect(getStoredLanguage()).toBe('bn');
    expect(getApiLanguage()).toBe('bn');
    expect(localStorage.getItem('chatflow-language')).toBe('bn');
  });

  it('falls back to English for unsupported values', () => {
    expect(setStoredLanguage('fr')).toBe('en');
    expect(getStoredLanguage()).toBe('en');
  });

  it('reads the persisted language during module initialization', async () => {
    localStorage.setItem('chatflow-language', 'bn');
    vi.resetModules();

    const language = await import('./language');

    expect(language.getStoredLanguage()).toBe('bn');
  });
});
