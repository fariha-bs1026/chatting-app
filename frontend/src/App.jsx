import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import {
  Bell,
  Camera,
  Check,
  Clock,
  Image,
  LogOut,
  MoreVertical,
  Pencil,
  Search,
  Send,
  Trash2,
  UserPlus,
  Users,
  X
} from 'lucide-react';
import { apiFetch, WS_URL } from './api';
import { clearStoredUser, getStoredUser, storeUser } from './authStorage';
import AuthViewPanel from './components/AuthView';
import ConversationAvatar from './components/ConversationAvatar';
import LanguageSelect from './components/LanguageSelect';
import LogoMark from './components/LogoMark';
import StatusIcon from './components/StatusIcon';
import ThemeToggle from './components/ThemeToggle';
import UserAvatar from './components/UserAvatar';
import { conversationSubtitle, conversationTitle, formatTime, initials } from './conversationUtils';
import { messageFromError } from './errors';
import { translate } from './i18n';
import { getStoredLanguage, setStoredLanguage } from './language';
import { IMAGE_ACCEPT, PHONE_PATTERN, normalizePhoneNumber, validateImageFile, validateProfileForm } from './validation';

const THEME_STORAGE_KEY = 'chatflow-theme';
const TEMPORARY_MESSAGE_OPTIONS = [
  { value: '', labelKey: 'message.temporaryOff' },
  { value: '3600', labelKey: 'message.temporaryOneHour' },
  { value: '86400', labelKey: 'message.temporaryOneDay' },
  { value: '604800', labelKey: 'message.temporarySevenDays' }
];

function isMessageDeleted(message) {
  return Boolean(message.deletedForEveryone || message.expired);
}

function isMessageExpired(message, now = Date.now()) {
  if (!message.expiresAt || isMessageDeleted(message)) {
    return false;
  }
  const expiresAt = new Date(message.expiresAt).getTime();
  return Number.isFinite(expiresAt) && expiresAt <= now;
}

function expiredMessage(message) {
  return {
    ...message,
    content: '',
    assetUrl: null,
    assetKey: null,
    type: 'TEXT',
    deletedForEveryone: true,
    expired: true,
    deletedAt: message.expiresAt || message.deletedAt
  };
}

function storedAuth() {
  const user = getStoredUser();
  return user ? { user } : null;
}

function storedTheme() {
  try {
    return localStorage.getItem(THEME_STORAGE_KEY) || 'light';
  } catch {
    return 'light';
  }
}

function App() {
  const [auth, setAuth] = useState(storedAuth);
  const [authChecked, setAuthChecked] = useState(false);
  const [theme, setTheme] = useState(storedTheme);
  const [language, setLanguage] = useState(getStoredLanguage);
  const [users, setUsers] = useState([]);
  const [conversations, setConversations] = useState([]);
  const [selectedConversation, setSelectedConversation] = useState(null);
  const [messages, setMessages] = useState([]);
  const [messagePage, setMessagePage] = useState({ nextBefore: null, hasMore: false });
  const [draft, setDraft] = useState('');
  const [selectedFile, setSelectedFile] = useState(null);
  const [selectedPreview, setSelectedPreview] = useState('');
  const [expiresInSeconds, setExpiresInSeconds] = useState('');
  const [messageMenuId, setMessageMenuId] = useState(null);
  const [uploadingMedia, setUploadingMedia] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [profileForm, setProfileForm] = useState({ displayName: '', phoneNumber: '' });
  const [profileFile, setProfileFile] = useState(null);
  const [profilePreview, setProfilePreview] = useState('');
  const [profileAvatarRemoved, setProfileAvatarRemoved] = useState(false);
  const [profileError, setProfileError] = useState('');
  const [savingProfile, setSavingProfile] = useState(false);
  const [search, setSearch] = useState('');
  const [sideView, setSideView] = useState('chats');
  const [peopleMode, setPeopleMode] = useState('contacts');
  const [groupName, setGroupName] = useState('');
  const [groupMembers, setGroupMembers] = useState([]);
  const [socketReady, setSocketReady] = useState(false);
  const [error, setError] = useState('');
  const [revealedConversationId, setRevealedConversationId] = useState(null);
  const stompRef = useRef(null);
  const swipeRef = useRef(null);
  const suppressSwipeClickRef = useRef(false);
  const readReceiptSentRef = useRef(new Set());

  const currentUser = auth?.user;
  const searchTerm = search.trim().toLowerCase();
  const t = useCallback((key, values) => translate(language, key, values), [language]);
  const conversationLabel = useCallback(
    (conversation) => conversationTitle(conversation, currentUser, language),
    [currentUser, language]
  );
  const conversationSubtext = useCallback(
    (conversation) => conversationSubtitle(conversation, currentUser, language),
    [currentUser, language]
  );
  const conversationPreview = useCallback((conversation) => {
    const lastMessage = conversation.lastMessage;
    if (!lastMessage) {
      return conversationSubtext(conversation);
    }
    const expired = lastMessage.expired || isMessageExpired(lastMessage);
    if (isMessageDeleted(lastMessage) || expired) {
      return expired ? t('message.expired') : t('message.deletedForEveryone');
    }
    if (lastMessage.content) {
      return lastMessage.content;
    }
    if (lastMessage.assetUrl || lastMessage.assetKey) {
      return lastMessage.type === 'IMAGE' ? t('message.sharedImage') : t('message.attachment');
    }
    return conversationSubtext(conversation);
  }, [conversationSubtext, t]);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  }, [theme]);

  useEffect(() => {
    const selectedLanguage = setStoredLanguage(language);
    document.documentElement.lang = selectedLanguage;
  }, [language]);

  useEffect(() => () => {
    if (selectedPreview) {
      URL.revokeObjectURL(selectedPreview);
    }
  }, [selectedPreview]);

  useEffect(() => () => {
    if (profilePreview) {
      URL.revokeObjectURL(profilePreview);
    }
  }, [profilePreview]);

  useEffect(() => {
    if (!currentUser || profileOpen) {
      return;
    }
    setProfileForm({
      displayName: currentUser.displayName || '',
      phoneNumber: currentUser.phoneNumber || ''
    });
  }, [currentUser, profileOpen]);

  const toggleTheme = useCallback(() => {
    setTheme((current) => current === 'dark' ? 'light' : 'dark');
  }, []);

  const changeLanguage = useCallback((nextLanguage) => {
    const selectedLanguage = setStoredLanguage(nextLanguage);
    document.documentElement.lang = selectedLanguage;
    setLanguage(selectedLanguage);
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
      const title = conversationLabel(conversation).toLowerCase();
      const subtitle = conversationSubtext(conversation).toLowerCase();
      const lastMessage = conversationPreview(conversation).toLowerCase();
      return title.includes(searchTerm) || subtitle.includes(searchTerm) || lastMessage.includes(searchTerm);
    });
  }, [conversationLabel, conversationPreview, conversationSubtext, conversations, currentUser, searchTerm]);

  const searchPlaceholder = sideView === 'people'
    ? peopleMode === 'contacts' ? t('search.contacts') : t('search.peopleByName')
    : sideView === 'groups' ? t('search.people') : t('search.chats');

  const saveAuth = useCallback((data) => {
    if (!data?.user) {
      clearStoredUser();
      setAuth(null);
      return;
    }
    storeUser(data.user);
    setAuth({ user: data.user });
  }, []);

  const mergeConversationUpdate = useCallback((updatedConversation) => {
    setConversations((current) => {
      const exists = current.some((conversation) => conversation.id === updatedConversation.id);
      const merged = exists
        ? current.map((conversation) => conversation.id === updatedConversation.id ? updatedConversation : conversation)
        : [updatedConversation, ...current];
      return [...merged].sort((first, second) =>
        new Date(second.updatedAt || 0).getTime() - new Date(first.updatedAt || 0).getTime()
      );
    });
    setSelectedConversation((current) =>
      current?.id === updatedConversation.id ? { ...current, ...updatedConversation } : current
    );
  }, []);

  function clearSelectedFile() {
    if (selectedPreview) {
      URL.revokeObjectURL(selectedPreview);
    }
    setSelectedFile(null);
    setSelectedPreview('');
  }

  function clearProfileFile() {
    if (profilePreview) {
      URL.revokeObjectURL(profilePreview);
    }
    setProfileFile(null);
    setProfilePreview('');
  }

  function openProfileEditor() {
    setProfileForm({
      displayName: currentUser.displayName || '',
      phoneNumber: currentUser.phoneNumber || ''
    });
    setProfileAvatarRemoved(false);
    clearProfileFile();
    setProfileOpen(true);
    setProfileError('');
    setError('');
  }

  function closeProfileEditor() {
    setProfileOpen(false);
    setProfileAvatarRemoved(false);
    setProfileError('');
    clearProfileFile();
  }

  function selectImage(event) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) {
      return;
    }
    const validationError = validateImageFile(file);
    if (validationError) {
      setError(validationError);
      return;
    }
    if (selectedPreview) {
      URL.revokeObjectURL(selectedPreview);
    }
    setSelectedFile(file);
    setSelectedPreview(URL.createObjectURL(file));
    setError('');
  }

  function selectProfileImage(event) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) {
      return;
    }
    const validationError = validateImageFile(file);
    if (validationError) {
      setProfileError(validationError);
      return;
    }
    if (profilePreview) {
      URL.revokeObjectURL(profilePreview);
    }
    setProfileFile(file);
    setProfilePreview(URL.createObjectURL(file));
    setProfileAvatarRemoved(false);
    setProfileError('');
    setError('');
  }

  async function uploadImageFile(file) {
    const body = new FormData();
    body.append('file', file);
    return apiFetch('/media', {
      method: 'POST',
      body
    });
  }

  async function uploadSelectedImage() {
    if (!selectedFile) {
      return null;
    }
    return uploadImageFile(selectedFile);
  }

  async function saveProfile(event) {
    event.preventDefault();
    const displayName = profileForm.displayName.trim();
    const phoneNumber = normalizePhoneNumber(profileForm.phoneNumber);
    const validationError = validateProfileForm({ displayName, phoneNumber });
    if (validationError) {
      setProfileError(validationError);
      return;
    }

    try {
      setSavingProfile(true);
      const media = profileFile ? await uploadImageFile(profileFile) : null;
      const updatedUser = await apiFetch('/users/me', {
        method: 'PATCH',
        body: {
          displayName,
          phoneNumber,
          avatarKey: media?.objectKey || null,
          removeAvatar: profileAvatarRemoved
        }
      });

      const nextAuth = { ...auth, user: updatedUser };
      saveAuth(nextAuth);
      setConversations((current) => current.map((conversation) => ({
        ...conversation,
        participants: conversation.participants.map((participant) =>
          participant.id === updatedUser.id ? { ...participant, ...updatedUser } : participant
        ),
        lastMessage: conversation.lastMessage?.sender?.id === updatedUser.id
          ? {
              ...conversation.lastMessage,
              sender: { ...conversation.lastMessage.sender, ...updatedUser }
            }
          : conversation.lastMessage
      })));
      setSelectedConversation((current) => current ? {
        ...current,
        participants: current.participants.map((participant) =>
          participant.id === updatedUser.id ? { ...participant, ...updatedUser } : participant
        )
      } : current);
      setMessages((current) => current.map((message) =>
        message.sender.id === updatedUser.id
          ? { ...message, sender: { ...message.sender, ...updatedUser } }
          : message
      ));
      closeProfileEditor();
      setProfileError('');
      setError('');
    } catch (exception) {
      setProfileError(messageFromError(exception));
    } finally {
      setSavingProfile(false);
    }
  }

  const loadUsers = useCallback(async () => {
    if (!currentUser?.id) {
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
    const data = await apiFetch(`/users?search=${encodeURIComponent(term)}`);
    setUsers(data);
  }, [currentUser?.id, peopleMode, search, sideView]);

  const loadConversations = useCallback(async () => {
    if (!currentUser?.id) {
      return;
    }
    const data = await apiFetch('/conversations');
    setConversations(data);
    setSelectedConversation((current) => {
      if (!current) {
        return current;
      }
      return data.find((conversation) => conversation.id === current.id) || current;
    });
  }, [currentUser?.id]);

  const openConversation = useCallback(async (conversation) => {
    const openedConversation = { ...conversation, unreadCount: 0 };
    setSelectedConversation(openedConversation);
    setMessageMenuId(null);
    setConversations((current) => current.map((item) =>
      item.id === conversation.id ? { ...item, unreadCount: 0 } : item
    ));
    const data = await apiFetch(`/conversations/${conversation.id}/messages?limit=50`);
    setMessages(data.messages || data);
    setMessagePage({
      nextBefore: data.nextBefore || null,
      hasMore: Boolean(data.hasMore)
    });
    setError('');
  }, []);

  const markMessagesRead = useCallback((messageList) => {
    if (!currentUser?.id || !selectedConversation?.id) {
      return;
    }

    messageList
      .filter((message) =>
        message.conversationId === selectedConversation.id
        && message.sender.id !== currentUser.id
        && message.status !== 'READ'
        && !isMessageDeleted(message)
        && !isMessageExpired(message)
        && !readReceiptSentRef.current.has(message.id)
      )
      .forEach((message) => {
        readReceiptSentRef.current.add(message.id);
        apiFetch(`/messages/${message.id}/status`, {
          method: 'PATCH',
          body: { status: 'READ' }
        }).catch(() => {
          readReceiptSentRef.current.delete(message.id);
        });
      });
  }, [currentUser?.id, selectedConversation?.id]);

  const loadOlderMessages = useCallback(async () => {
    if (!selectedConversation || !messagePage.hasMore) {
      return;
    }
    const query = new URLSearchParams({ limit: '50' });
    if (messagePage.nextBefore) {
      query.set('before', messagePage.nextBefore);
    }
    try {
      const data = await apiFetch(`/conversations/${selectedConversation.id}/messages?${query.toString()}`);
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
      setError(messageFromError(exception));
    }
  }, [messagePage.hasMore, messagePage.nextBefore, selectedConversation]);

  useEffect(() => {
    let active = true;
    apiFetch('/auth/me')
      .then((user) => {
        if (active) {
          saveAuth({ user });
        }
      })
      .catch(() => {
        if (active) {
          clearStoredUser();
          setAuth(null);
        }
      })
      .finally(() => {
        if (active) {
          setAuthChecked(true);
        }
      });
    return () => {
      active = false;
    };
  }, [saveAuth]);

  useEffect(() => {
    if (!currentUser?.id) {
      return;
    }
    loadUsers().catch((exception) => setError(messageFromError(exception)));
    loadConversations().catch((exception) => setError(messageFromError(exception)));
  }, [currentUser?.id, loadConversations, loadUsers]);

  useEffect(() => {
    if (!currentUser?.id) {
      return undefined;
    }

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: {},
      reconnectDelay: 3000,
      debug: () => {},
      onConnect: () => {
        setSocketReady(true);
        client.subscribe('/user/queue/conversations', (payload) => {
          mergeConversationUpdate(JSON.parse(payload.body));
        });
      },
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
  }, [currentUser?.id, mergeConversationUpdate]);

  useEffect(() => {
    if (!selectedConversation || !socketReady || !stompRef.current) {
      return undefined;
    }

    const messageSubscription = stompRef.current.subscribe(
      `/topic/conversations/${selectedConversation.id}`,
      (payload) => {
        const incoming = JSON.parse(payload.body);
        setMessages((current) => current.some((message) => message.id === incoming.id)
          ? current.map((message) => message.id === incoming.id ? { ...message, ...incoming } : message)
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

  useEffect(() => {
    markMessagesRead(messages);
  }, [markMessagesRead, messages]);

  useEffect(() => {
    const futureExpirations = messages
      .filter((message) => message.expiresAt && !isMessageDeleted(message))
      .map((message) => new Date(message.expiresAt).getTime())
      .filter((expiresAt) => Number.isFinite(expiresAt));

    if (futureExpirations.length === 0) {
      return undefined;
    }

    const now = Date.now();
    const nextExpiration = Math.min(...futureExpirations);
    const timeoutId = window.setTimeout(() => {
      setMessages((current) => current.map((message) =>
        isMessageExpired(message) ? expiredMessage(message) : message
      ));
    }, Math.max(0, Math.min(nextExpiration - now, 2147483647)));

    return () => window.clearTimeout(timeoutId);
  }, [messages]);

  useEffect(() => {
    const futureExpirations = conversations
      .map((conversation) => conversation.lastMessage)
      .filter((message) => message?.expiresAt && !isMessageDeleted(message))
      .map((message) => new Date(message.expiresAt).getTime())
      .filter((expiresAt) => Number.isFinite(expiresAt));

    if (futureExpirations.length === 0) {
      return undefined;
    }

    const now = Date.now();
    const nextExpiration = Math.min(...futureExpirations);
    const timeoutId = window.setTimeout(() => {
      setConversations((current) => current.map((conversation) => {
        const lastMessage = conversation.lastMessage;
        return lastMessage && isMessageExpired(lastMessage)
          ? { ...conversation, lastMessage: expiredMessage(lastMessage) }
          : conversation;
      }));
      setSelectedConversation((current) => {
        const lastMessage = current?.lastMessage;
        return lastMessage && isMessageExpired(lastMessage)
          ? { ...current, lastMessage: expiredMessage(lastMessage) }
          : current;
      });
    }, Math.max(0, Math.min(nextExpiration - now, 2147483647)));

    return () => window.clearTimeout(timeoutId);
  }, [conversations]);

  async function logout() {
    try {
      await apiFetch('/auth/logout', {
        method: 'POST'
      });
    } catch {}
    clearStoredUser();
    setAuth(null);
    setSelectedConversation(null);
    setMessages([]);
    setMessagePage({ nextBefore: null, hasMore: false });
    setExpiresInSeconds('');
    setMessageMenuId(null);
    clearSelectedFile();
  }

  async function startDirectChat(user) {
    try {
      const conversation = await apiFetch('/conversations/direct', {
        method: 'POST',
        body: { userId: user.id }
      });
      await loadConversations();
      await openConversation(conversation);
      setSideView('chats');
    } catch (exception) {
      setError(messageFromError(exception));
    }
  }

  async function createGroup(event) {
    event.preventDefault();
    try {
      const conversation = await apiFetch('/conversations/groups', {
        method: 'POST',
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
      setError(messageFromError(exception));
    }
  }

  async function deleteConversation(conversation) {
    if (!conversation) {
      return;
    }
    const confirmed = window.confirm(t('chat.deleteConfirm'));
    if (!confirmed) {
      return;
    }
    try {
      await apiFetch(`/conversations/${conversation.id}`, {
        method: 'DELETE'
      });
      setConversations((current) => current.filter((item) => item.id !== conversation.id));
      setRevealedConversationId(null);
      if (selectedConversation?.id === conversation.id) {
        setSelectedConversation(null);
        setMessages([]);
        setMessagePage({ nextBefore: null, hasMore: false });
        clearSelectedFile();
      }
      setError('');
    } catch (exception) {
      setError(messageFromError(exception));
    }
  }

  async function deleteSelectedConversation() {
    await deleteConversation(selectedConversation);
  }

  async function deleteMessage(message, scope) {
    try {
      const result = await apiFetch(`/messages/${message.id}?scope=${scope}`, {
        method: 'DELETE'
      });
      setMessageMenuId(null);
      if (scope === 'ME') {
        setMessages((current) => current.filter((item) => item.id !== message.id));
      } else {
        setMessages((current) => current.map((item) =>
          item.id === message.id ? { ...item, ...result } : item
        ));
      }
      await loadConversations();
      setError('');
    } catch (exception) {
      setError(messageFromError(exception));
    }
  }

  function startConversationSwipe(event, conversationId) {
    if (event.pointerType === 'mouse' && event.button !== 0) {
      return;
    }
    swipeRef.current = {
      conversationId,
      startX: event.clientX,
      startY: event.clientY,
      dragging: false
    };
    event.currentTarget.setPointerCapture?.(event.pointerId);
  }

  function moveConversationSwipe(event, conversationId) {
    const swipe = swipeRef.current;
    if (!swipe || swipe.conversationId !== conversationId) {
      return;
    }

    const deltaX = event.clientX - swipe.startX;
    const deltaY = event.clientY - swipe.startY;
    if (Math.abs(deltaX) > 10 && Math.abs(deltaX) > Math.abs(deltaY)) {
      swipe.dragging = true;
      event.preventDefault();
    }
  }

  function endConversationSwipe(event, conversationId) {
    const swipe = swipeRef.current;
    if (!swipe || swipe.conversationId !== conversationId) {
      return;
    }

    const deltaX = event.clientX - swipe.startX;
    if (swipe.dragging) {
      suppressSwipeClickRef.current = true;
      window.setTimeout(() => {
        suppressSwipeClickRef.current = false;
      }, 120);

      if (deltaX < -36) {
        setRevealedConversationId(conversationId);
      } else if (deltaX > 24) {
        setRevealedConversationId(null);
      }
    }
    swipeRef.current = null;
  }

  async function sendMessage(event) {
    event.preventDefault();
    const content = draft.trim();
    if (!selectedConversation || (!content && !selectedFile)) {
      return;
    }

    try {
      setUploadingMedia(Boolean(selectedFile));
      const media = await uploadSelectedImage();
      const payload = {
        conversationId: selectedConversation.id,
        content,
        type: media ? 'IMAGE' : 'TEXT',
        assetUrl: null,
        assetKey: media?.objectKey || null,
        expiresInSeconds: expiresInSeconds ? Number(expiresInSeconds) : null
      };

      setDraft('');
      clearSelectedFile();

      if (socketReady && stompRef.current?.connected) {
        stompRef.current.publish({
          destination: '/app/chat.send',
          body: JSON.stringify(payload)
        });
        return;
      }

      const message = await apiFetch(`/conversations/${selectedConversation.id}/messages`, {
        method: 'POST',
        body: payload
      });
      setMessages((current) => [...current, message]);
      await loadConversations();
    } catch (exception) {
      setError(messageFromError(exception));
    } finally {
      setUploadingMedia(false);
    }
  }

  function toggleGroupMember(userId) {
    setGroupMembers((current) =>
      current.includes(userId) ? current.filter((id) => id !== userId) : [...current, userId]
    );
  }

  if (!authChecked) {
    return null;
  }

  if (!auth) {
    return (
      <AuthViewPanel
        onAuth={saveAuth}
        theme={theme}
        onToggleTheme={toggleTheme}
        language={language}
        onLanguageChange={changeLanguage}
      />
    );
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <header className="brand-header">
          <div className="brand-row compact">
            <LogoMark />
            <strong>ChatFlow</strong>
          </div>
          <div className="header-tools">
            <LanguageSelect language={language} onChange={changeLanguage} />
            <ThemeToggle theme={theme} onToggle={toggleTheme} />
          </div>
        </header>

        <header className="profile-bar">
          <UserAvatar user={currentUser} />
          <div className="profile-copy">
            <strong>{currentUser.displayName}</strong>
            <span>@{currentUser.username}</span>
          </div>
          <button className="icon-button" type="button" onClick={openProfileEditor} title={t('profile.edit')} aria-label={t('profile.edit')}>
            <Pencil size={17} aria-hidden="true" />
          </button>
          <button className="icon-button" type="button" onClick={logout} title={t('profile.logout')} aria-label={t('profile.logout')}>
            <LogOut size={18} aria-hidden="true" />
          </button>
        </header>

        {profileOpen && (
          <form className="profile-editor" onSubmit={saveProfile}>
            <div className="profile-photo-row">
              <label className="profile-photo-picker" title={t('profile.changePhoto')}>
                <div className="avatar large">
                  {profilePreview && <img src={profilePreview} alt="" />}
                  {!profilePreview && !profileAvatarRemoved && currentUser.avatarUrl && <img src={currentUser.avatarUrl} alt="" />}
                  {!profilePreview && (profileAvatarRemoved || !currentUser.avatarUrl) && initials(profileForm.displayName)}
                </div>
                <span>
                  <Camera size={15} aria-hidden="true" />
                  {t('profile.changePhoto')}
                </span>
                <input
                  type="file"
                  accept={IMAGE_ACCEPT}
                  onChange={selectProfileImage}
                />
              </label>
              {(profilePreview || currentUser.avatarUrl) && !profileAvatarRemoved && (
                <button
                  className="icon-button danger profile-photo-delete"
                  type="button"
                  onClick={() => {
                    clearProfileFile();
                    setProfileAvatarRemoved(true);
                  }}
                  title={t('profile.deletePhoto')}
                  aria-label={t('profile.deletePhoto')}
                >
                  <Trash2 size={18} aria-hidden="true" />
                </button>
              )}
            </div>
            {profileError && <div className="profile-error" role="alert">{profileError}</div>}

            <label>
              {t('auth.displayName')}
              <input
                value={profileForm.displayName}
                onChange={(event) => setProfileForm({ ...profileForm, displayName: event.target.value })}
                minLength={2}
                maxLength={120}
                required
              />
            </label>
            <label>
              {t('auth.phoneNumber')}
              <input
                value={profileForm.phoneNumber}
                onChange={(event) => setProfileForm({ ...profileForm, phoneNumber: event.target.value })}
                maxLength={16}
                pattern={PHONE_PATTERN}
                required
              />
            </label>

            <div className="profile-editor-actions">
              <button className="secondary-action" type="submit" disabled={savingProfile}>
                <Check size={16} aria-hidden="true" />
                {t('profile.save')}
              </button>
              <button className="text-action" type="button" onClick={closeProfileEditor}>
                {t('profile.cancel')}
              </button>
            </div>
          </form>
        )}

        <div className={`connection-row ${socketReady ? 'online' : 'offline'}`}>
          <span className="presence-dot" aria-hidden="true" />
          <span>{socketReady ? t('connection.live') : t('connection.reconnecting')}</span>
        </div>

        <label className="search-box">
          <Search size={17} aria-hidden="true" />
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder={searchPlaceholder}
            maxLength={80}
          />
        </label>

        <div className="side-tabs" role="tablist" aria-label={t('tab.chats')}>
          <button className={sideView === 'chats' ? 'active' : ''} type="button" onClick={() => setSideView('chats')}>
            <LogoMark size="tiny" />
            {t('tab.chats')}
          </button>
          <button className={sideView === 'people' ? 'active' : ''} type="button" onClick={() => setSideView('people')}>
            <UserPlus size={15} aria-hidden="true" />
            {t('tab.people')}
          </button>
          <button className={sideView === 'groups' ? 'active' : ''} type="button" onClick={() => setSideView('groups')}>
            <Users size={15} aria-hidden="true" />
            {t('tab.groups')}
          </button>
        </div>

        {sideView === 'chats' && (
          <div className="list-scroll">
            {visibleConversations.map((conversation) => (
              <div
                className={`conversation-swipe-row ${revealedConversationId === conversation.id ? 'revealed' : ''}`}
                key={conversation.id}
              >
                <button
                  className="swipe-delete-action"
                  type="button"
                  onClick={() => deleteConversation(conversation)}
                  title={t('chat.deleteChat')}
                  aria-label={`${t('chat.delete')} ${conversationLabel(conversation)}`}
                >
                  <Trash2 size={18} aria-hidden="true" />
                  <span>{t('chat.delete')}</span>
                </button>
                <button
                  className={`conversation-item ${selectedConversation?.id === conversation.id ? 'active' : ''}`}
                  type="button"
                  onPointerDown={(event) => startConversationSwipe(event, conversation.id)}
                  onPointerMove={(event) => moveConversationSwipe(event, conversation.id)}
                  onPointerUp={(event) => endConversationSwipe(event, conversation.id)}
                  onPointerCancel={() => {
                    swipeRef.current = null;
                  }}
                  onClick={() => {
                    if (suppressSwipeClickRef.current) {
                      return;
                    }
                    setRevealedConversationId(null);
                    openConversation(conversation);
                  }}
                >
                  <ConversationAvatar conversation={conversation} currentUser={currentUser} size="small" />
                  <div className="conversation-copy">
                    <strong>{conversationLabel(conversation)}</strong>
                    <span>{conversationPreview(conversation)}</span>
                  </div>
                  <div className="conversation-meta">
                    <time>{formatTime(conversation.lastMessage?.createdAt || conversation.updatedAt)}</time>
                    {conversation.unreadCount > 0 && (
                      <span className="unread-badge" title={t('chat.unread', { count: conversation.unreadCount })}>
                        <Bell size={11} aria-hidden="true" />
                        {conversation.unreadCount > 9 ? '9+' : conversation.unreadCount}
                      </span>
                    )}
                  </div>
                </button>
              </div>
            ))}
            {conversations.length === 0 && <p className="empty-copy">{t('chat.noConversations')}</p>}
            {conversations.length > 0 && visibleConversations.length === 0 && <p className="empty-copy">{t('chat.noChatsFound')}</p>}
          </div>
        )}

        {sideView === 'people' && (
          <div className="list-scroll">
            <div className="people-tabs" role="tablist" aria-label={t('tab.people')}>
              <button
                className={peopleMode === 'contacts' ? 'active' : ''}
                type="button"
                onClick={() => setPeopleMode('contacts')}
              >
                <Users size={15} aria-hidden="true" />
                {t('tab.contacts')}
              </button>
              <button
                className={peopleMode === 'search' ? 'active' : ''}
                type="button"
                onClick={() => setPeopleMode('search')}
              >
                <Search size={15} aria-hidden="true" />
                {t('tab.search')}
              </button>
            </div>

            {peopleMode === 'contacts' && visibleContacts.map((user) => (
              <button className="user-item" key={user.id} type="button" onClick={() => startDirectChat(user)}>
                <UserAvatar user={user} size="small" />
                <div className="conversation-copy">
                  <strong>{user.displayName}</strong>
                  <span>{user.online ? t('conversation.online') : `@${user.username}`}</span>
                </div>
                <LogoMark size="tiny" />
              </button>
            ))}
            {peopleMode === 'contacts' && contactUsers.length === 0 && (
              <p className="empty-copy">{t('people.noContacts')}</p>
            )}
            {peopleMode === 'contacts' && contactUsers.length > 0 && visibleContacts.length === 0 && (
              <p className="empty-copy">{t('people.noContactsFound')}</p>
            )}

            {peopleMode === 'search' && searchTerm.length < 2 && (
              <p className="empty-copy">{t('people.searchHint')}</p>
            )}
            {peopleMode === 'search' && visibleUsers.map((user) => (
              <button className="user-item" key={user.id} type="button" onClick={() => startDirectChat(user)}>
                <UserAvatar user={user} size="small" />
                <div className="conversation-copy">
                  <strong>{user.displayName}</strong>
                  <span>{user.online ? t('conversation.online') : `@${user.username}`}</span>
                </div>
                <UserPlus size={17} aria-hidden="true" />
              </button>
            ))}
            {peopleMode === 'search' && searchTerm.length >= 2 && visibleUsers.length === 0 && (
              <p className="empty-copy">{t('people.noUsersFound')}</p>
            )}
          </div>
        )}

        {sideView === 'groups' && (
          <form className="group-panel" onSubmit={createGroup}>
            <label>
              {t('group.name')}
              <input
                value={groupName}
                onChange={(event) => setGroupName(event.target.value)}
                maxLength={120}
                required
              />
            </label>
            <div className="member-list">
              {searchTerm.length < 2 && (
                <p className="empty-copy">{t('group.searchMembers')}</p>
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
              {searchTerm.length >= 2 && visibleUsers.length === 0 && <p className="empty-copy">{t('people.noUsersFound')}</p>}
            </div>
            <button className="secondary-action" type="submit" disabled={!groupName.trim() || groupMembers.length === 0}>
              <Users size={17} aria-hidden="true" />
              {t('group.create')}
            </button>
          </form>
        )}
      </aside>

      <main className="chat-pane">
        {selectedConversation ? (
          <>
            <header className="chat-header">
              <ConversationAvatar conversation={selectedConversation} currentUser={currentUser} />
              <div className="chat-title-block">
                <h2>{conversationLabel(selectedConversation)}</h2>
                <span>{conversationSubtext(selectedConversation)}</span>
              </div>
              <div className={`status-pill ${socketReady ? 'online' : 'offline'}`}>
                <span className="presence-dot" aria-hidden="true" />
                {socketReady ? t('connection.live') : t('connection.offline')}
              </div>
              <button
                className="icon-button danger"
                type="button"
                onClick={deleteSelectedConversation}
                title={t('chat.deleteChat')}
                aria-label={t('chat.deleteChat')}
              >
                <Trash2 size={18} aria-hidden="true" />
              </button>
            </header>

            <div className="chat-body">
              <section className="message-list" aria-live="polite">
                {messagePage.hasMore && (
                  <button className="load-more-messages" type="button" onClick={loadOlderMessages}>
                    {t('message.loadOlder')}
                  </button>
                )}
                {messages.length === 0 && (
                  <div className="thread-empty">
                    <LogoMark />
                    <p>{t('message.empty')}</p>
                  </div>
                )}
                {messages.map((message) => {
                  const mine = message.sender.id === currentUser.id;
                  const deleted = isMessageDeleted(message) || isMessageExpired(message);
                  const tombstone = message.expired || isMessageExpired(message)
                    ? t('message.expired')
                    : t('message.deletedForEveryone');
                  return (
                    <article className={`message-bubble ${mine ? 'mine' : 'theirs'} ${deleted ? 'deleted' : ''}`} key={message.id}>
                      <div className="message-menu">
                        <button
                          className="message-menu-trigger"
                          type="button"
                          onClick={() => setMessageMenuId((current) => current === message.id ? null : message.id)}
                          title={t('message.moreActions')}
                          aria-label={t('message.moreActions')}
                          aria-expanded={messageMenuId === message.id}
                        >
                          <MoreVertical size={15} aria-hidden="true" />
                        </button>
                        {messageMenuId === message.id && (
                          <div className="message-menu-panel">
                            <button type="button" onClick={() => deleteMessage(message, 'ME')}>
                              {t('message.deleteForMe')}
                            </button>
                            {mine && !deleted && (
                              <button type="button" onClick={() => deleteMessage(message, 'EVERYONE')}>
                                {t('message.deleteForEveryone')}
                              </button>
                            )}
                          </div>
                        )}
                      </div>
                      {!mine && <strong>{message.sender.displayName}</strong>}
                      {deleted && <p className="message-tombstone">{tombstone}</p>}
                      {!deleted && message.assetUrl && message.type === 'IMAGE' && (
                        <a className="image-attachment" href={message.assetUrl} target="_blank" rel="noreferrer">
                          <img src={message.assetUrl} alt={message.content || t('message.sharedImage')} loading="lazy" />
                        </a>
                      )}
                      {!deleted && message.assetUrl && message.type !== 'IMAGE' && (
                        <a className="asset-link" href={message.assetUrl} target="_blank" rel="noreferrer">
                          <Image size={16} aria-hidden="true" />
                          {t('message.attachment')}
                        </a>
                      )}
                      {!deleted && message.content && <p>{message.content}</p>}
                      <footer>
                        <time>{formatTime(message.createdAt)}</time>
                        {mine && <StatusIcon status={message.status} />}
                      </footer>
                    </article>
                  );
                })}
              </section>

              <aside className="contact-panel">
                <span className="panel-kicker">{t('contact.info')}</span>
                <div className="contact-card">
                  <ConversationAvatar conversation={selectedConversation} currentUser={currentUser} size="large" />
                  <strong>{conversationLabel(selectedConversation)}</strong>
                  <span>{conversationSubtext(selectedConversation)}</span>
                </div>

                <div className="contact-actions" aria-label={t('contact.actions')}>
                  <button type="button">{t('contact.audio')}</button>
                  <button type="button">{t('contact.video')}</button>
                  <button type="button">{t('contact.search')}</button>
                </div>

                <div className="info-stack">
                  <div>
                    <span>{t('contact.messages')}</span>
                    <strong>{messages.length}</strong>
                  </div>
                  <div>
                    <span>{t('contact.members')}</span>
                    <strong>{selectedConversation.participants.length}</strong>
                  </div>
                </div>
              </aside>
            </div>

            {error && <div className="toast">{error}</div>}

            <form className="composer" onSubmit={sendMessage}>
              {selectedPreview && (
                <div className="selected-media-preview">
                  <img src={selectedPreview} alt={t('message.selectedUpload')} />
                  <div>
                    <strong>{selectedFile?.name}</strong>
                    <span>{Math.ceil((selectedFile?.size || 0) / 1024)} KB</span>
                  </div>
                  <button type="button" onClick={clearSelectedFile} title={t('message.removeImage')} aria-label={t('message.removeImage')}>
                    <X size={16} aria-hidden="true" />
                  </button>
                </div>
              )}
              <label className="media-picker" title={t('message.attachImage')} aria-label={t('message.attachImage')}>
                <Image size={18} aria-hidden="true" />
                <input
                  type="file"
                  accept={IMAGE_ACCEPT}
                  onChange={selectImage}
                />
              </label>
              <label className="temporary-picker" title={t('message.temporary')}>
                <Clock size={16} aria-hidden="true" />
                <select
                  value={expiresInSeconds}
                  onChange={(event) => setExpiresInSeconds(event.target.value)}
                  aria-label={t('message.temporary')}
                >
                  {TEMPORARY_MESSAGE_OPTIONS.map((option) => (
                    <option key={option.value || 'off'} value={option.value}>
                      {t(option.labelKey)}
                    </option>
                  ))}
                </select>
              </label>
              <label className="composer-field message-field">
                <input
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  placeholder={t('message.placeholder')}
                  aria-label={t('message.placeholder')}
                  maxLength={4000}
                />
              </label>
              <button
                className="send-button"
                type="submit"
                title={t('message.send')}
                aria-label={t('message.send')}
                disabled={uploadingMedia}
              >
                <Send size={19} aria-hidden="true" />
              </button>
            </form>
          </>
        ) : (
          <section className="empty-state">
            <LogoMark size="large" />
            <h2>ChatFlow</h2>
            <p>{t('empty.selectConversation')}</p>
          </section>
        )}
      </main>
    </div>
  );
}

export default App;
