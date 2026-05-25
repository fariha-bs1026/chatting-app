import { describe, expect, it } from 'vitest';
import { conversationSubtitle, conversationTitle, directParticipant, initials } from './conversationUtils';

const currentUser = { id: 'alice-id', username: 'alice', displayName: 'Alice' };
const bob = { id: 'bob-id', username: 'bob', displayName: 'Bob', online: true };

describe('conversation utilities', () => {
  it('builds initials safely', () => {
    expect(initials('Alice Baker')).toBe('AB');
    expect(initials('')).toBe('?');
  });

  it('finds the other participant for direct conversations', () => {
    const conversation = { direct: true, participants: [currentUser, bob] };

    expect(directParticipant(conversation, currentUser)).toBe(bob);
  });

  it('formats direct and group titles', () => {
    expect(conversationTitle({ direct: true, participants: [currentUser, bob] }, currentUser)).toBe('Bob');
    expect(conversationTitle({ direct: false, name: 'Team', participants: [] }, currentUser)).toBe('Team');
    expect(conversationTitle({ direct: false, participants: [] }, currentUser)).toBe('Group');
  });

  it('formats subtitles from presence and membership', () => {
    expect(conversationSubtitle({ direct: true, participants: [currentUser, bob] }, currentUser)).toBe('Online');
    expect(conversationSubtitle({ direct: false, participants: [currentUser, bob] }, currentUser)).toBe('2 members');
  });
});
