import { getApiLanguage } from './language';

export const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
export const MAX_MEDIA_BYTES = 50 * 1024 * 1024;
export const IMAGE_ACCEPT = 'image/jpeg,image/png,image/gif,image/webp';
export const MEDIA_ACCEPT = [
  'image/jpeg',
  'image/png',
  'image/gif',
  'image/webp',
  'audio/mpeg',
  'audio/wav',
  'audio/ogg',
  'audio/webm',
  'video/mp4',
  'video/webm'
].join(',');
export const PHONE_PATTERN = '^\\+[1-9]\\d{7,14}$';

const ACCEPTED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp']);
const ACCEPTED_MEDIA_TYPES = new Set(MEDIA_ACCEPT.split(','));

export const validationMessages = {
  en: {
    imageType: 'Choose a JPG, PNG, GIF, or WebP image.',
    imageSize: 'Image must be 10 MB or smaller.',
    mediaType: 'Choose a supported image, audio, or video file.',
    mediaSize: 'Media file must be 50 MB or smaller.',
    displayName: 'Display name must be 2-120 characters.',
    phoneNumber: 'Phone number must look like +8801712345678.'
  },
  bn: {
    mediaType: 'সমর্থিত ছবি, অডিও, অথবা ভিডিও ফাইল নির্বাচন করুন।',
    mediaSize: 'মিডিয়া ফাইল ৫০ MB বা তার কম হতে হবে।',
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

export function validateMediaFile(file) {
  if (!file) {
    return null;
  }
  if (!ACCEPTED_MEDIA_TYPES.has(file.type)) {
    return message('mediaType');
  }
  if (file.size > MAX_MEDIA_BYTES) {
    return message('mediaSize');
  }
  return null;
}

export function messageTypeForFile(file) {
  if (!file?.type) {
    return 'FILE';
  }
  if (file.type.startsWith('image/')) {
    return 'IMAGE';
  }
  if (file.type.startsWith('audio/')) {
    return 'AUDIO';
  }
  if (file.type.startsWith('video/')) {
    return 'VIDEO';
  }
  return 'FILE';
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
