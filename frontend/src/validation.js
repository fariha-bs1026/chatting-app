import { getApiLanguage } from './language';

export const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
export const IMAGE_ACCEPT = 'image/jpeg,image/png,image/gif,image/webp';
export const PHONE_PATTERN = '^\\+[1-9]\\d{7,14}$';

const ACCEPTED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp']);

export const validationMessages = {
  en: {
    imageType: 'Choose a JPG, PNG, GIF, or WebP image.',
    imageSize: 'Image must be 10 MB or smaller.',
    displayName: 'Display name must be 2-120 characters.',
    phoneNumber: 'Phone number must look like +8801712345678.'
  },
  bn: {
    imageType: 'JPG, PNG, GIF, অথবা WebP ছবি নির্বাচন করুন।',
    imageSize: 'ছবিটি ১০ MB বা তার কম হতে হবে।',
    displayName: 'ডিসপ্লে নাম ২-১২০ অক্ষরের হতে হবে।',
    phoneNumber: 'ফোন নম্বর +8801712345678 এর মতো হতে হবে।'
  }
};

export function normalizePhoneNumber(value) {
  return (value || '').trim().replaceAll(' ', '');
}

export function validateImageFile(file) {
  if (!file) {
    return null;
  }
  if (!ACCEPTED_IMAGE_TYPES.has(file.type)) {
    return message('imageType');
  }
  if (file.size > MAX_IMAGE_BYTES) {
    return message('imageSize');
  }
  return null;
}

export function validateProfileForm({ displayName, phoneNumber }) {
  const cleanDisplayName = (displayName || '').trim();
  const cleanPhoneNumber = normalizePhoneNumber(phoneNumber);

  if (cleanDisplayName.length < 2 || cleanDisplayName.length > 120) {
    return message('displayName');
  }
  if (!new RegExp(PHONE_PATTERN).test(cleanPhoneNumber)) {
    return message('phoneNumber');
  }
  return null;
}

function message(key) {
  const language = getApiLanguage();
  return validationMessages[language]?.[key] || validationMessages.en[key];
}
