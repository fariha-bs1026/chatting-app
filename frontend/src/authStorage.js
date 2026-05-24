const AUTH_USER_STORAGE_KEY = 'chatting-app-user';

export function getStoredUser() {
  try {
    const value = sessionStorage.getItem(AUTH_USER_STORAGE_KEY);
    return value ? JSON.parse(value) : null;
  } catch {
    return null;
  }
}

export function storeUser(user) {
  try {
    sessionStorage.setItem(AUTH_USER_STORAGE_KEY, JSON.stringify(user));
  } catch {
  }
}

export function clearStoredUser() {
  try {
    sessionStorage.removeItem(AUTH_USER_STORAGE_KEY);
  } catch {
  }
}
