import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import {
  Image,
  LogOut,
  Search,
  Send,
  UserPlus,
  Users,
  Wifi,
  WifiOff
} from 'lucide-react';
import { apiFetch, WS_URL } from './api';
import AuthViewPanel from './components/AuthView';
import LogoMark from './components/LogoMark';
import StatusIcon from './components/StatusIcon';
import ThemeToggle from './components/ThemeToggle';

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

function App() {
  const [auth, setAuth] = useState(storedAuth);
  const [theme, setTheme] = useState(storedTheme);
  const [users, setUsers] = useState([]);
  const [conversations, setConversations] = useState([]);
  const [selectedConversation, setSelectedConversation] = useState(null);
  const [messages, setMessages] = useState([]);
  const [messagePage, setMessagePage] = useState({ nextBefore: null, hasMore: false });
  const [draft, setDraft] = useState('');
  const [assetUrl, setAssetUrl] = useState('');
  const [search, setSearch] = useState('');
  const [sideView, setSideView] = useState('chats');
  const [peopleMode, setPeopleMode] = useState('contacts');
  const [groupName, setGroupName] = useState('');
  const [groupMembers, setGroupMembers] = useState([]);
  const [socketReady, setSocketReady] = useState(false);
  const [error, setError] = useState('');
  const stompRef = useRef(null);

  const currentUser = auth?.user;
  const searchTerm = search.trim().toLowerCase();

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  }, [theme]);

  const toggleTheme = useCallback(() => {
    setTheme((current) => current === 'dark' ? 'light' : 'dark');
  }, []);

  const visibleUsers = useMemo(() => {
    if (searchTerm.length < 2) {
      return [];
    }
    return users.filter((user) =>
      user.displayName.toLowerCase().includes(searchTerm) || user.username.toLowerCase().includes(searchTerm)
    );
  }, [searchTerm, users]);

  const contactUsers = useMemo(() => {
    if (!currentUser) {
      return [];
    }

    const contacts = new Map();
    conversations
      .filter((conversation) => conversation.direct)
      .forEach((conversation) => {
        const contact = conversation.participants.find((user) => user.id !== currentUser.id);
        if (contact) {
          contacts.set(contact.id, contact);
        }
      });

    return Array.from(contacts.values()).sort((first, second) =>
      first.displayName.localeCompare(second.displayName)
    );
  }, [conversations, currentUser]);

  const visibleContacts = useMemo(() => {
    if (!searchTerm) {
      return contactUsers;
    }
    return contactUsers.filter((user) =>
      user.displayName.toLowerCase().includes(searchTerm) || user.username.toLowerCase().includes(searchTerm)
    );
  }, [contactUsers, searchTerm]);

  const visibleConversations = useMemo(() => {
    if (!currentUser) {
      return [];
    }

    if (!searchTerm) {
      return conversations;
    }
    return conversations.filter((conversation) => {
      const title = conversationTitle(conversation, currentUser).toLowerCase();
      const subtitle = conversationSubtitle(conversation, currentUser).toLowerCase();
      const lastMessage = conversation.lastMessage?.content?.toLowerCase() || '';
      return title.includes(searchTerm) || subtitle.includes(searchTerm) || lastMessage.includes(searchTerm);
    });
  }, [conversations, currentUser, searchTerm]);

  const searchPlaceholder = sideView === 'people'
    ? peopleMode === 'contacts' ? 'Search contacts' : 'Search by name or username'
    : sideView === 'groups' ? 'Search people' : 'Search chats';

  const saveAuth = useCallback((data) => {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(data));
    setAuth(data);
  }, []);

  const loadUsers = useCallback(async () => {
    if (!auth?.token) {
      return;
    }
    const shouldSearchUsers = sideView === 'groups' || (sideView === 'people' && peopleMode === 'search');
    if (!shouldSearchUsers) {
      setUsers([]);
      return;
    }
    const term = search.trim();
    if (term.length < 2) {
      setUsers([]);
      return;
    }
    const data = await apiFetch(`/users?search=${encodeURIComponent(term)}`, {
      token: auth.token
    });
    setUsers(data);
  }, [auth?.token, peopleMode, search, sideView]);

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
    const data = await apiFetch(`/conversations/${conversation.id}/messages?limit=50`, {
      token: auth.token
    });
    setMessages(data.messages || data);
    setMessagePage({
      nextBefore: data.nextBefore || null,
      hasMore: Boolean(data.hasMore)
    });
    setError('');
  }, [auth?.token]);

  const loadOlderMessages = useCallback(async () => {
    if (!selectedConversation || !messagePage.hasMore) {
      return;
    }
    const query = new URLSearchParams({ limit: '50' });
    if (messagePage.nextBefore) {
      query.set('before', messagePage.nextBefore);
    }
    try {
      const data = await apiFetch(`/conversations/${selectedConversation.id}/messages?${query.toString()}`, {
        token: auth.token
      });
      setMessages((current) => {
        const existingIds = new Set(current.map((message) => message.id));
        const olderMessages = (data.messages || []).filter((message) => !existingIds.has(message.id));
        return [...olderMessages, ...current];
      });
      setMessagePage({
        nextBefore: data.nextBefore || null,
        hasMore: Boolean(data.hasMore)
      });
    } catch (exception) {
      setError(exception.message);
    }
  }, [auth?.token, messagePage.hasMore, messagePage.nextBefore, selectedConversation]);

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
    } catch {}
    localStorage.removeItem(AUTH_STORAGE_KEY);
    setAuth(null);
    setSelectedConversation(null);
    setMessages([]);
    setMessagePage({ nextBefore: null, hasMore: false });
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
    return <AuthViewPanel onAuth={saveAuth} theme={theme} onToggleTheme={toggleTheme} />;
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
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder={searchPlaceholder} />
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
            {visibleConversations.map((conversation) => (
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
            {conversations.length > 0 && visibleConversations.length === 0 && <p className="empty-copy">No chats found.</p>}
          </div>
        )}

        {sideView === 'people' && (
          <div className="list-scroll">
            <div className="people-tabs" role="tablist" aria-label="People source">
              <button
                className={peopleMode === 'contacts' ? 'active' : ''}
                type="button"
                onClick={() => setPeopleMode('contacts')}
              >
                <Users size={15} aria-hidden="true" />
                Contacts
              </button>
              <button
                className={peopleMode === 'search' ? 'active' : ''}
                type="button"
                onClick={() => setPeopleMode('search')}
              >
                <Search size={15} aria-hidden="true" />
                Search
              </button>
            </div>

            {peopleMode === 'contacts' && visibleContacts.map((user) => (
              <button className="user-item" key={user.id} type="button" onClick={() => startDirectChat(user)}>
                <div className="avatar small">{initials(user.displayName)}</div>
                <div className="conversation-copy">
                  <strong>{user.displayName}</strong>
                  <span>{user.online ? 'Online' : `@${user.username}`}</span>
                </div>
                <LogoMark size="tiny" />
              </button>
            ))}
            {peopleMode === 'contacts' && contactUsers.length === 0 && (
              <p className="empty-copy">No contacts yet. Use Search to start a chat.</p>
            )}
            {peopleMode === 'contacts' && contactUsers.length > 0 && visibleContacts.length === 0 && (
              <p className="empty-copy">No contacts found.</p>
            )}

            {peopleMode === 'search' && searchTerm.length < 2 && (
              <p className="empty-copy">Search by name or username to find someone new.</p>
            )}
            {peopleMode === 'search' && visibleUsers.map((user) => (
              <button className="user-item" key={user.id} type="button" onClick={() => startDirectChat(user)}>
                <div className="avatar small">{initials(user.displayName)}</div>
                <div className="conversation-copy">
                  <strong>{user.displayName}</strong>
                  <span>{user.online ? 'Online' : `@${user.username}`}</span>
                </div>
                <UserPlus size={17} aria-hidden="true" />
              </button>
            ))}
            {peopleMode === 'search' && searchTerm.length >= 2 && visibleUsers.length === 0 && (
              <p className="empty-copy">No users found.</p>
            )}
          </div>
        )}

        {sideView === 'groups' && (
          <form className="group-panel" onSubmit={createGroup}>
            <label>
              Group name
              <input value={groupName} onChange={(event) => setGroupName(event.target.value)} required />
            </label>
            <div className="member-list">
              {searchTerm.length < 2 && (
                <p className="empty-copy">Search people to add group members.</p>
              )}
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
              {searchTerm.length >= 2 && visibleUsers.length === 0 && <p className="empty-copy">No users found.</p>}
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
                {messagePage.hasMore && (
                  <button className="load-more-messages" type="button" onClick={loadOlderMessages}>
                    Load older messages
                  </button>
                )}
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
