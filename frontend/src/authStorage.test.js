import { afterEach, describe, expect, it } from 'vitest';
import { clearStoredUser, getStoredUser, storeUser } from './authStorage';

afterEach(() => {
  sessionStorage.clear();
});

describe('auth storage', () => {
  it('stores only the current user payload in session storage', () => {
    const user = { id: 'user-1', username: 'alice', displayName: 'Alice' };

    storeUser(user);

    expect(getStoredUser()).toEqual(user);
    expect(sessionStorage.getItem('chatting-app-user')).toBe(JSON.stringify(user));
  });

  it('clears stored user data', () => {
    storeUser({ id: 'user-1' });

    clearStoredUser();

    expect(getStoredUser()).toBeNull();
  });
});
