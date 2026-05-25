import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiFetch } from './api';
import { setStoredLanguage } from './language';

afterEach(() => {
  vi.unstubAllGlobals();
  setStoredLanguage('en');
});

describe('apiFetch', () => {
  it('sends JSON requests with credentials and selected language', async () => {
    setStoredLanguage('bn');
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), { status: 200 })
    );
    vi.stubGlobal('fetch', fetchMock);

    const response = await apiFetch('/users', {
      method: 'POST',
      body: { name: 'Alice' }
    });

    expect(response).toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/users', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept-Language': 'bn',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ name: 'Alice' })
    });
  });

  it('does not force JSON headers for form uploads', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);
    const body = new FormData();
    body.append('file', new Blob(['data'], { type: 'image/png' }), 'photo.png');

    await apiFetch('/media', { method: 'POST', body });

    const [, options] = fetchMock.mock.calls[0];
    expect(options.headers).toEqual({ 'Accept-Language': 'en' });
    expect(options.body).toBe(body);
  });

  it('throws server-provided error messages', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ message: 'Invalid media file' }), { status: 400 })
    ));

    await expect(apiFetch('/media')).rejects.toThrow('Invalid media file');
  });
});
