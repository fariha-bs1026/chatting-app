import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import ConversationAvatar from './ConversationAvatar';
import UserAvatar from './UserAvatar';

const currentUser = { id: 'alice-id', displayName: 'Alice' };
const bob = { id: 'bob-id', displayName: 'Bob Baker', avatarUrl: 'https://example.com/bob.png' };

describe('avatar components', () => {
  it('renders uploaded user avatars when available', () => {
    const { container } = render(<UserAvatar user={bob} />);

    expect(container.querySelector('img')).toHaveAttribute('src', 'https://example.com/bob.png');
  });

  it('falls back to initials for users without avatars', () => {
    const { container } = render(<UserAvatar user={{ displayName: 'Alice Baker' }} />);

    expect(container).toHaveTextContent('AB');
  });

  it('renders direct participant avatars and group icons', () => {
    const direct = {
      direct: true,
      participants: [currentUser, { ...bob, avatarUrl: null }]
    };
    const { rerender, container } = render(<ConversationAvatar conversation={direct} currentUser={currentUser} />);

    expect(container).toHaveTextContent('BB');

    rerender(<ConversationAvatar conversation={{ direct: false, participants: [] }} currentUser={currentUser} />);
    expect(container.querySelector('.avatar svg')).toBeInTheDocument();
  });
});
