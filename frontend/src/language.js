export const SUPPORTED_LANGUAGES = [
  { code: 'en', label: 'English' },
  { code: 'bn', label: 'বাংলা' }
];

const LANGUAGE_STORAGE_KEY = 'chatflow-language';
let currentLanguage = readStoredLanguage();

export function getStoredLanguage() {
  return currentLanguage;
}

export function setStoredLanguage(language) {
  currentLanguage = normalizeLanguage(language);
  try {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, currentLanguage);
  } catch {
  }
  return currentLanguage;
}

export function getApiLanguage() {
  return currentLanguage;
}

function readStoredLanguage() {
  try {
    return normalizeLanguage(localStorage.getItem(LANGUAGE_STORAGE_KEY));
  } catch {
    return 'en';
  }
}

function normalizeLanguage(language) {
  return SUPPORTED_LANGUAGES.some((item) => item.code === language) ? language : 'en';
}
