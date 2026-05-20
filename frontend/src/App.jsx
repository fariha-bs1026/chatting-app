import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import {
  Check,
  CheckCheck,
  Image,
  LogOut,
  Moon,
  Search,
  Send,
  Sun,
  UserPlus,
  Users,
  Wifi,
  WifiOff
} from 'lucide-react';
import { apiFetch, WS_URL } from './api';

const AUTH_STORAGE_KEY = 'chatting-app-auth';
const THEME_STORAGE_KEY = 'chatflow-theme';

function storedAuth() {
  try {
    return JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY));
  } catch {
    return null;
  }
}

function storedTheme() {
  try {
    return localStorage.getItem(THEME_STORAGE_KEY) || 'light';
  } catch {
    return 'light';
  }
}

function initials(name) {
  return (name || '?')
    .split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
}

function formatTime(value) {
  if (!value) {
    return '';
  }
  return new Intl.DateTimeFormat(undefined, {
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value));
}

function conversationTitle(conversation, currentUser) {
  if (!conversation) {
    return '';
  }
  if (!conversation.direct) {
    return conversation.name || 'Group';
  }
  const other = conversation.participants.find((user) => user.id !== currentUser.id);
  return other?.displayName || 'Direct chat';
}

function conversationSubtitle(conversation, currentUser) {
  if (!conversation) {
    return '';
  }
  if (!conversation.direct) {
    return `${conversation.participants.length} members`;
  }
  const other = conversation.participants.find((user) => user.id !== currentUser.id);
  if (!other) {
    return '';
  }
  return other.online ? 'Online' : other.lastSeenAt ? `Last seen ${formatTime(other.lastSeenAt)}` : 'Offline';
}

function LogoMark({ size = 'default' }) {
  const sizeClass = size === 'large' ? 'large' : size === 'tiny' ? 'tiny' : '';
  return (
    <span className={`logo-mark ${sizeClass}`} aria-hidden="true">
      <svg viewBox="0 0 40 40" role="img">
        <path
          className="logo-bubble"
          d="M10.4 11.6c0-3 2.4-5.4 5.4-5.4h8.4c3 0 5.4 2.4 5.4 5.4v6.8c0 3-2.4 5.4-5.4 5.4h-4.7l-6 5.1c-.9.7-2.2.1-2.2-1v-4.2c-2.7-.3-4.9-2.6-4.9-5.3v-6.8Z"
        />
        <path
          className="logo-heart"
          d="M19.9 18.8c-3.7-2.2-5.5-3.8-5.5-6.1 0-1.5 1.2-2.7 2.8-2.7 1.1 0 2 .5 2.7 1.5.7-1 1.6-1.5 2.7-1.5 1.6 0 2.8 1.2 2.8 2.7 0 2.3-1.8 3.9-5.5 6.1Z"
        />
        <circle className="logo-dot" cx="30.2" cy="29.7" r="3.8" />
      </svg>
    </span>
  );
}

function ThemeToggle({ theme, onToggle }) {
  const isDark = theme === 'dark';
  return (
    <button
      className="theme-toggle"
      type="button"
      onClick={onToggle}
      title={isDark ? 'Switch to white mode' : 'Switch to dark mode'}
      aria-label={isDark ? 'Switch to white mode' : 'Switch to dark mode'}
    >
      {isDark ? <Sun size={16} aria-hidden="true" /> : <Moon size={16} aria-hidden="true" />}
      <span>{isDark ? 'White' : 'Dark'}</span>
    </button>
  );
}

function StatusIcon({ status }) {
  if (status === 'READ') {
    return <CheckCheck className="status-icon read" size={15} aria-hidden="true" />;
  }
  if (status === 'DELIVERED') {
    return <CheckCheck className="status-icon delivered" size={15} aria-hidden="true" />;
  }
  return <Check className="status-icon sent" size={15} aria-hidden="true" />;
}

function AuthView({ onAuth, theme, onToggleTheme }) {
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({
    username: '',
    displayName: '',
    password: ''
  });
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const path = mode === 'login' ? '/auth/login' : '/auth/register';
      const body = mode === 'login'
        ? { username: form.username, password: form.password }
        : form;
      const data = await apiFetch(path, {
        method: 'POST',
        body
      });
      onAuth(data);
    } catch (exception) {
      setError(exception.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="auth-screen">
      <section className="auth-showcase" aria-hidden="true">
        <div className="showcase-brand">
          <LogoMark size="large" />
          <div>
            <span>ChatFlow</span>
            <strong>Realtime conversations</strong>
          </div>
        </div>

        <div className="phone-preview">
          <div className="preview-topbar">
            <div className="avatar small">FA</div>
            <div>
              <strong>Fariha</strong>
              <span>Online</span>
            </div>
          </div>
          <div className="preview-thread">
            <div className="preview-bubble their-preview">Are we testing the mobile app next?</div>
            <div className="preview-bubble my-preview">Yes, same Spring Boot API.</div>
            <div className="preview-bubble their-preview">Perfect. Web and Flutter together.</div>
          </div>
          <div className="preview-composer">
            <span>Message</span>
            <Send size={15} aria-hidden="true" />
          </div>
        </div>
      </section>

      <form className="auth-panel" onSubmit={submit}>
        <ThemeToggle theme={theme} onToggle={onToggleTheme} />

        <div className="brand-row">
          <LogoMark />
          <div>
            <h1>ChatFlow</h1>
            <p>{mode === 'login' ? 'Welcome back' : 'Create your account'}</p>
          </div>
        </div>

        <div className="mode-tabs" role="tablist" aria-label="Authentication mode">
          <button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>
            Login
          </button>
          <button type="button" className={mode === 'register' ? 'active' : ''} onClick={() => setMode('register')}>
            Register
          </button>
        </div>

        <label>
          Username
          <input
            value={form.username}
            onChange={(event) => setForm({ ...form, username: event.target.value })}
            autoComplete="username"
            minLength={3}
            required
          />
        </label>

        {mode === 'register' && (
          <label>
            Display name
            <input
              value={form.displayName}
              onChange={(event) => setForm({ ...form, displayName: event.target.value })}
              autoComplete="name"
              minLength={2}
              required
            />
          </label>
        )}

        <label>
          Password
          <input
            value={form.password}
            onChange={(event) => setForm({ ...form, password: event.target.value })}
            type="password"
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            minLength={6}
            required
          />
        </label>

        {error && <p className="form-error">{error}</p>}

        <button className="primary-action" type="submit" disabled={submitting}>
          <LogoMark size="tiny" />
          {submitting ? 'Working...' : mode === 'login' ? 'Login' : 'Create account'}
        </button>
      </form>
    </main>
  );
}

function App() {
  const [auth, setAuth] = useState(storedAuth);
  const [theme, setTheme] = useState(storedTheme);
  const [users, setUsers] = useState([]);
  const [conversations, setConversations] = useState([]);
  const [selectedConversation, setSelectedConversation] = useState(null);
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [assetUrl, setAssetUrl] = useState('');
  const [search, setSearch] = useState('');
  const [sideView, setSideView] = useState('chats');
  const [groupName, setGroupName] = useState('');
  const [groupMembers, setGroupMembers] = useState([]);
  const [socketReady, setSocketReady] = useState(false);
  const [error, setError] = useState('');
  const stompRef = useRef(null);

  const currentUser = auth?.user;

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  }, [theme]);

  const toggleTheme = useCallback(() => {
    setTheme((current) => current === 'dark' ? 'light' : 'dark');
  }, []);

  const visibleUsers = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) {
      return users;
    }
    return users.filter((user) =>
      user.displayName.toLowerCase().includes(term) || user.username.toLowerCase().includes(term)
    );
  }, [search, users]);

  const saveAuth = useCallback((data) => {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(data));
    setAuth(data);
  }, []);

  const loadUsers = useCallback(async () => {
    if (!auth?.token) {
      return;
    }
    const data = await apiFetch(`/users?search=${encodeURIComponent(search)}`, {
      token: auth.token
    });
    setUsers(data);
  }, [auth?.token, search]);

  const loadConversations = useCallback(async () => {
    if (!auth?.token) {
      return;
    }
    const data = await apiFetch('/conversations', {
      token: auth.token
    });
    setConversations(data);
    setSelectedConversation((current) => {
      if (!current) {
        return current;
      }
      return data.find((conversation) => conversation.id === current.id) || current;
    });
  }, [auth?.token]);

  const openConversation = useCallback(async (conversation) => {
    setSelectedConversation(conversation);
    const data = await apiFetch(`/conversations/${conversation.id}/messages`, {
      token: auth.token
    });
    setMessages(data);
    setError('');
  }, [auth?.token]);

  useEffect(() => {
    if (!auth?.token) {
      return;
    }
    loadUsers().catch((exception) => setError(exception.message));
    loadConversations().catch((exception) => setError(exception.message));
  }, [auth?.token, loadConversations, loadUsers]);

  useEffect(() => {
    if (!auth?.token) {
      return undefined;
    }

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: {
        Authorization: `Bearer ${auth.token}`
      },
      reconnectDelay: 3000,
      debug: () => {},
      onConnect: () => setSocketReady(true),
      onDisconnect: () => setSocketReady(false),
      onWebSocketClose: () => setSocketReady(false),
      onStompError: (frame) => setError(frame.headers.message || 'WebSocket error')
    });

    stompRef.current = client;
    client.activate();

    return () => {
      setSocketReady(false);
      client.deactivate();
      stompRef.current = null;
    };
  }, [auth?.token]);

  useEffect(() => {
    if (!selectedConversation || !socketReady || !stompRef.current) {
      return undefined;
    }

    const messageSubscription = stompRef.current.subscribe(
      `/topic/conversations/${selectedConversation.id}`,
      (payload) => {
        const incoming = JSON.parse(payload.body);
        setMessages((current) => current.some((message) => message.id === incoming.id)
          ? current
          : [...current, incoming]);
        loadConversations().catch(() => {});
      }
    );

    const statusSubscription = stompRef.current.subscribe(
      `/topic/conversations/${selectedConversation.id}/status`,
      (payload) => {
        const event = JSON.parse(payload.body);
        setMessages((current) => current.map((message) =>
          message.id === event.messageId ? { ...message, status: event.status } : message
        ));
      }
    );

    return () => {
      messageSubscription.unsubscribe();
      statusSubscription.unsubscribe();
    };
  }, [selectedConversation, socketReady, loadConversations]);

  async function logout() {
    try {
      await apiFetch('/auth/logout', {
        method: 'POST',
        token: auth.token
      });
    } catch {
      // Local logout should still clear the browser state.
    }
    localStorage.removeItem(AUTH_STORAGE_KEY);
    setAuth(null);
    setSelectedConversation(null);
    setMessages([]);
  }

  async function startDirectChat(user) {
    try {
      const conversation = await apiFetch('/conversations/direct', {
        method: 'POST',
        token: auth.token,
        body: { userId: user.id }
      });
      await loadConversations();
      await openConversation(conversation);
      setSideView('chats');
    } catch (exception) {
      setError(exception.message);
    }
  }

  async function createGroup(event) {
    event.preventDefault();
    try {
      const conversation = await apiFetch('/conversations/groups', {
        method: 'POST',
        token: auth.token,
        body: {
          name: groupName,
          memberIds: groupMembers
        }
      });
      setGroupName('');
      setGroupMembers([]);
      await loadConversations();
      await openConversation(conversation);
      setSideView('chats');
    } catch (exception) {
      setError(exception.message);
    }
  }

  async function sendMessage(event) {
    event.preventDefault();
    const content = draft.trim();
    const media = assetUrl.trim();
    if (!selectedConversation || (!content && !media)) {
      return;
    }

    const payload = {
      conversationId: selectedConversation.id,
      content,
      type: media ? 'FILE' : 'TEXT',
      assetUrl: media || null
    };

    setDraft('');
    setAssetUrl('');

    if (socketReady && stompRef.current?.connected) {
      stompRef.current.publish({
        destination: '/app/chat.send',
        body: JSON.stringify(payload)
      });
      return;
    }

    try {
      const message = await apiFetch(`/conversations/${selectedConversation.id}/messages`, {
        method: 'POST',
        token: auth.token,
        body: payload
      });
      setMessages((current) => [...current, message]);
      await loadConversations();
    } catch (exception) {
      setError(exception.message);
    }
  }

  function toggleGroupMember(userId) {
    setGroupMembers((current) =>
      current.includes(userId) ? current.filter((id) => id !== userId) : [...current, userId]
    );
  }

  if (!auth) {
    return <AuthView onAuth={saveAuth} theme={theme} onToggleTheme={toggleTheme} />;
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <header className="brand-header">
          <div className="brand-row compact">
            <LogoMark />
            <strong>ChatFlow</strong>
          </div>
          <ThemeToggle theme={theme} onToggle={toggleTheme} />
        </header>

        <header className="profile-bar">
          <div className="avatar">{initials(currentUser.displayName)}</div>
          <div className="profile-copy">
            <strong>{currentUser.displayName}</strong>
            <span>@{currentUser.username}</span>
          </div>
          <button className="icon-button" type="button" onClick={logout} title="Logout" aria-label="Logout">
            <LogOut size={18} aria-hidden="true" />
          </button>
        </header>

        <div className={`connection-row ${socketReady ? 'online' : 'offline'}`}>
          {socketReady ? <Wifi size={16} aria-hidden="true" /> : <WifiOff size={16} aria-hidden="true" />}
          <span>{socketReady ? 'Live' : 'Reconnecting'}</span>
        </div>

        <label className="search-box">
          <Search size={17} aria-hidden="true" />
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search" />
        </label>

        <div className="side-tabs" role="tablist" aria-label="Sidebar view">
          <button className={sideView === 'chats' ? 'active' : ''} type="button" onClick={() => setSideView('chats')}>
            <LogoMark size="tiny" />
            Chats
          </button>
          <button className={sideView === 'people' ? 'active' : ''} type="button" onClick={() => setSideView('people')}>
            <UserPlus size={15} aria-hidden="true" />
            People
          </button>
          <button className={sideView === 'groups' ? 'active' : ''} type="button" onClick={() => setSideView('groups')}>
            <Users size={15} aria-hidden="true" />
            Groups
          </button>
        </div>

        {sideView === 'chats' && (
          <div className="list-scroll">
            {conversations.map((conversation) => (
              <button
                className={`conversation-item ${selectedConversation?.id === conversation.id ? 'active' : ''}`}
                key={conversation.id}
                type="button"
                onClick={() => openConversation(conversation)}
              >
                <div className="avatar small">{conversation.direct ? initials(conversationTitle(conversation, currentUser)) : <Users size={17} />}</div>
                <div className="conversation-copy">
                  <strong>{conversationTitle(conversation, currentUser)}</strong>
                  <span>{conversation.lastMessage?.content || conversationSubtitle(conversation, currentUser)}</span>
                </div>
                <time>{formatTime(conversation.lastMessage?.createdAt || conversation.updatedAt)}</time>
              </button>
            ))}
            {conversations.length === 0 && <p className="empty-copy">No conversations yet.</p>}
          </div>
        )}

        {sideView === 'people' && (
          <div className="list-scroll">
            {visibleUsers.map((user) => (
              <button className="user-item" key={user.id} type="button" onClick={() => startDirectChat(user)}>
                <div className="avatar small">{initials(user.displayName)}</div>
                <div className="conversation-copy">
                  <strong>{user.displayName}</strong>
                  <span>{user.online ? 'Online' : `@${user.username}`}</span>
                </div>
                <UserPlus size={17} aria-hidden="true" />
              </button>
            ))}
            {visibleUsers.length === 0 && <p className="empty-copy">No users found.</p>}
          </div>
        )}

        {sideView === 'groups' && (
          <form className="group-panel" onSubmit={createGroup}>
            <label>
              Group name
              <input value={groupName} onChange={(event) => setGroupName(event.target.value)} required />
            </label>
            <div className="member-list">
              {visibleUsers.map((user) => (
                <label className="member-row" key={user.id}>
                  <input
                    type="checkbox"
                    checked={groupMembers.includes(user.id)}
                    onChange={() => toggleGroupMember(user.id)}
                  />
                  <span>{user.displayName}</span>
                </label>
              ))}
            </div>
            <button className="secondary-action" type="submit">
              <Users size={17} aria-hidden="true" />
              Create group
            </button>
          </form>
        )}
      </aside>

      <main className="chat-pane">
        {selectedConversation ? (
          <>
            <header className="chat-header">
              <div className="avatar">{selectedConversation.direct ? initials(conversationTitle(selectedConversation, currentUser)) : <Users size={22} />}</div>
              <div className="chat-title-block">
                <h2>{conversationTitle(selectedConversation, currentUser)}</h2>
                <span>{conversationSubtitle(selectedConversation, currentUser)}</span>
              </div>
              <div className={`status-pill ${socketReady ? 'online' : 'offline'}`}>
                {socketReady ? <Wifi size={14} aria-hidden="true" /> : <WifiOff size={14} aria-hidden="true" />}
                {socketReady ? 'Live' : 'Offline'}
              </div>
            </header>

            <div className="chat-body">
              <section className="message-list" aria-live="polite">
                {messages.length === 0 && (
                  <div className="thread-empty">
                    <LogoMark />
                    <p>No messages yet.</p>
                  </div>
                )}
                {messages.map((message) => {
                  const mine = message.sender.id === currentUser.id;
                  return (
                    <article className={`message-bubble ${mine ? 'mine' : 'theirs'}`} key={message.id}>
                      {!mine && <strong>{message.sender.displayName}</strong>}
                      {message.assetUrl && (
                        <a className="asset-link" href={message.assetUrl} target="_blank" rel="noreferrer">
                          <Image size={16} aria-hidden="true" />
                          Attachment
                        </a>
                      )}
                      {message.content && <p>{message.content}</p>}
                      <footer>
                        <time>{formatTime(message.createdAt)}</time>
                        {mine && <StatusIcon status={message.status} />}
                      </footer>
                    </article>
                  );
                })}
              </section>

              <aside className="contact-panel">
                <span className="panel-kicker">Contact Info</span>
                <div className="contact-card">
                  <div className="avatar large">
                    {selectedConversation.direct ? initials(conversationTitle(selectedConversation, currentUser)) : <Users size={28} />}
                  </div>
                  <strong>{conversationTitle(selectedConversation, currentUser)}</strong>
                  <span>{conversationSubtitle(selectedConversation, currentUser)}</span>
                </div>

                <div className="contact-actions" aria-label="Contact actions">
                  <button type="button">Audio</button>
                  <button type="button">Video</button>
                  <button type="button">Search</button>
                </div>

                <div className="info-stack">
                  <div>
                    <span>Messages</span>
                    <strong>{messages.length}</strong>
                  </div>
                  <div>
                    <span>Members</span>
                    <strong>{selectedConversation.participants.length}</strong>
                  </div>
                </div>
              </aside>
            </div>

            {error && <div className="toast">{error}</div>}

            <form className="composer" onSubmit={sendMessage}>
              <label className="composer-field asset-field">
                <Image size={17} aria-hidden="true" />
                <input
                  className="asset-input"
                  value={assetUrl}
                  onChange={(event) => setAssetUrl(event.target.value)}
                  placeholder="Asset URL"
                  aria-label="Asset URL"
                />
              </label>
              <label className="composer-field message-field">
                <input
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  placeholder="Message"
                  aria-label="Message"
                />
              </label>
              <button className="send-button" type="submit" title="Send" aria-label="Send">
                <Send size={19} aria-hidden="true" />
              </button>
            </form>
          </>
        ) : (
          <section className="empty-state">
            <LogoMark size="large" />
            <h2>ChatFlow</h2>
            <p>Select a conversation or start a new one.</p>
          </section>
        )}
      </main>
    </div>
  );
}

export default App;
