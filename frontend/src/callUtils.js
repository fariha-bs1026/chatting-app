export const CALL_SIGNAL_DESTINATION = '/app/call.signal';
export const CALL_ICE_SERVERS = [{ urls: 'stun:stun.l.google.com:19302' }];

export function createCallId() {
  return crypto.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function parseCallPayload(signal) {
  if (!signal?.payload) {
    return {};
  }
  try {
    return JSON.parse(signal.payload);
  } catch {
    return {};
  }
}

export function mediaConstraintsForCall(mode) {
  return {
    audio: true,
    video: mode === 'VIDEO'
  };
}

export function callMediaErrorKey(error, mode) {
  const name = error?.name || '';
  const message = (error?.message || '').toLowerCase();
  const video = mode === 'VIDEO';

  if (name === 'NotAllowedError' || name === 'SecurityError') {
    return 'call.mediaPermissionDenied';
  }
  if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
    return video ? 'call.cameraNotFound' : 'call.microphoneNotFound';
  }
  if (
    name === 'NotReadableError'
    || name === 'TrackStartError'
    || message.includes('could not start video source')
  ) {
    return video ? 'call.cameraUnavailable' : 'call.microphoneUnavailable';
  }
  if (name === 'OverconstrainedError' || name === 'ConstraintNotSatisfiedError') {
    return video ? 'call.cameraConstraint' : 'call.microphoneConstraint';
  }
  return 'call.mediaUnavailable';
}
