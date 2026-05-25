import { describe, expect, it, vi } from 'vitest';
import {
  callMediaErrorKey,
  createCallId,
  mediaConstraintsForCall,
  parseCallPayload
} from './callUtils';

describe('call utilities', () => {
  it('parses call signaling payloads defensively', () => {
    expect(parseCallPayload({ payload: '{"sdp":"offer"}' })).toEqual({ sdp: 'offer' });
    expect(parseCallPayload({ payload: 'not-json' })).toEqual({});
    expect(parseCallPayload({})).toEqual({});
  });

  it('creates browser call ids with crypto when available', () => {
    const randomUUID = vi.spyOn(crypto, 'randomUUID').mockReturnValue('call-id');

    expect(createCallId()).toBe('call-id');

    randomUUID.mockRestore();
  });

  it('maps browser media failures to translation keys', () => {
    expect(callMediaErrorKey({ name: 'NotAllowedError' }, 'VIDEO')).toBe('call.mediaPermissionDenied');
    expect(callMediaErrorKey({ name: 'NotFoundError' }, 'VIDEO')).toBe('call.cameraNotFound');
    expect(callMediaErrorKey({ name: 'NotFoundError' }, 'AUDIO')).toBe('call.microphoneNotFound');
    expect(callMediaErrorKey({ name: 'NotReadableError' }, 'VIDEO')).toBe('call.cameraUnavailable');
    expect(callMediaErrorKey({ message: 'Could not start video source' }, 'VIDEO')).toBe('call.cameraUnavailable');
  });

  it('builds media constraints from call mode', () => {
    expect(mediaConstraintsForCall('AUDIO')).toEqual({ audio: true, video: false });
    expect(mediaConstraintsForCall('VIDEO')).toEqual({ audio: true, video: true });
  });
});
