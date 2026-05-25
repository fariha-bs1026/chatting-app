import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiFetch } from './api';
import { clearStoredUser, storeUser } from './authStorage';
import App from './App';

const socketInstance = {
  activate: vi.fn(),
  deactivate: vi.fn(),
  subscribe: vi.fn()
};

vi.mock('./api', () => ({
  apiFetch: vi.fn(),
  WS_URL: 'http://localhost:8080/ws'
}));

vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn(function ClientMock() {
    return socketInstance;
  })
}));

vi.mock('sockjs-client', () => ({
  default: vi.fn()
}));

beforeEach(() => {
  apiFetch.mockReset();
  socketInstance.activate.mockClear();
  socketInstance.deactivate.mockClear();
  socketInstance.subscribe.mockClear();
  clearStoredUser();
});

describe('App shell', () => {
  it('renders the auth screen when current session lookup fails', async () => {
    apiFetch.mockRejectedValue(new Error('Unauthorized'));

    render(<App />);

    expect(await screen.findByText('Welcome back')).toBeInTheDocument();
  });

  it('renders an authenticated empty chat shell and starts the socket client', async () => {
    const user = {
      id: 'alice-id',
      username: 'alice',
      displayName: 'Alice',
      phoneNumber: '+8801712345678'
    };
    storeUser(user);
    apiFetch.mockImplementation((path) => {
      if (path === '/auth/me') {
        return Promise.resolve(user);
      }
      if (path === '/conversations') {
        return Promise.resolve([]);
      }
      return Promise.resolve([]);
    });

    render(<App />);

    expect(await screen.findByText('No conversations yet.')).toBeInTheDocument();
    expect(screen.getByText('Select a conversation or start a new one.')).toBeInTheDocument();
    await waitFor(() => expect(socketInstance.activate).toHaveBeenCalled());
  });

  it('opens a conversation and renders message history', async () => {
    const user = {
      id: 'alice-id',
      username: 'alice',
      displayName: 'Alice',
      phoneNumber: '+8801712345678'
    };
    const bob = {
      id: 'bob-id',
      username: 'bob',
      displayName: 'Bob',
      online: true
    };
    const conversation = {
      id: 'conversation-1',
      direct: true,
      participants: [user, bob],
      unreadCount: 1,
      updatedAt: '2026-05-25T01:00:00Z',
      lastMessage: {
        id: 'message-1',
        conversationId: 'conversation-1',
        sender: bob,
        content: 'Hello Alice',
        type: 'TEXT',
        status: 'SENT',
        createdAt: '2026-05-25T01:00:00Z'
      }
    };
    storeUser(user);
    apiFetch.mockImplementation((path) => {
      if (path === '/auth/me') {
        return Promise.resolve(user);
      }
      if (path === '/conversations') {
        return Promise.resolve([conversation]);
      }
      if (path === '/conversations/conversation-1/messages?limit=50') {
        return Promise.resolve({
          messages: [conversation.lastMessage],
          nextBefore: null,
          hasMore: false
        });
      }
      if (path === '/messages/message-1/status') {
        return Promise.resolve({ ...conversation.lastMessage, status: 'READ' });
      }
      return Promise.resolve([]);
    });

    const userEvent = await import('@testing-library/user-event');
    const actor = userEvent.default.setup();
    render(<App />);

    const conversationButton = await screen.findAllByRole('button', { name: /Bob/ })
      .then((buttons) => buttons.find((button) => button.className.includes('conversation-item')));
    await actor.click(conversationButton);

    expect(await screen.findByRole('heading', { name: 'Bob' })).toBeInTheDocument();
    expect(screen.getAllByText('Hello Alice').length).toBeGreaterThanOrEqual(2);
    expect(apiFetch).toHaveBeenCalledWith('/conversations/conversation-1/messages?limit=50');
  });
});
