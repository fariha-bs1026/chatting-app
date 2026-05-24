export const fallbackErrorMessage = 'Something went wrong. Please try again.';

export function messageFromError(error, fallback = fallbackErrorMessage) {
  return error?.message || fallback;
}
