import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import {
  Bell,
  Camera,
  Check,
  Clock,
  FileAudio,
  Image,
  LogOut,
  Mic,
  MoreVertical,
  Paperclip,
  Pencil,
  Phone,
  PhoneOff,
  Search,
  Send,
  Trash2,
  UserPlus,
  Video,
  Users,
  X
} from 'lucide-react';
import { apiFetch, WS_URL } from './api';
import { clearStoredUser, getStoredUser, storeUser } from './authStorage';
import {
  CALL_ICE_SERVERS,
  CALL_SIGNAL_DESTINATION,
  callMediaErrorKey,
  createCallId,
  mediaConstraintsForCall,
  parseCallPayload
} from './callUtils';
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
import {
  expiredMessage,
  isMessageDeleted,
  isMessageExpired
} from './messageUtils';
import {
  IMAGE_ACCEPT,
  MEDIA_ACCEPT,
  PHONE_PATTERN,
  messageTypeForFile,
  normalizePhoneNumber,
  validateImageFile,
  validateMediaFile,
  validateProfileForm
} from './validation';

const THEME_STORAGE_KEY = 'chatflow-theme';
const TEMPORARY_MESSAGE_OPTIONS = [
  { value: '', labelKey: 'message.temporaryOff' },
  { value: '3600', labelKey: 'message.temporaryOneHour' },
  { value: '86400', labelKey: 'message.temporaryOneDay' },
  { value: '604800', labelKey: 'message.temporarySevenDays' }
];

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
  const [typingUsers, setTypingUsers] = useState({});
  const [selectedFile, setSelectedFile] = useState(null);
  const [selectedPreview, setSelectedPreview] = useState('');
  const [expiresInSeconds, setExpiresInSeconds] = useState('');
  const [messageMenuId, setMessageMenuId] = useState(null);
  const [uploadingMedia, setUploadingMedia] = useState(false);
  const [callState, setCallState] = useState({
    status: 'idle',
    mode: null,
    callId: null,
    conversationId: null,
    incoming: null,
    callerUsername: null
  });
  const [localStream, setLocalStream] = useState(null);
  const [remoteStream, setRemoteStream] = useState(null);
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
  const peerRef = useRef(null);
  const localStreamRef = useRef(null);
  const remoteStreamRef = useRef(null);
  const pendingIceCandidatesRef = useRef([]);
  const callStateRef = useRef(callState);
  const typingActiveRef = useRef(false);
  const typingConversationRef = useRef(null);
  const typingStopTimeoutRef = useRef(null);
  const typingClearTimeoutsRef = useRef(new Map());
  const currentUserRef = useRef(null);
  const selectedConversationRef = useRef(null);
  const localVideoRef = useRef(null);
  const remoteVideoRef = useRef(null);
  const remoteAudioRef = useRef(null);

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
      if (lastMessage.type === 'IMAGE') {
        return t('message.sharedImage');
      }
      if (lastMessage.type === 'AUDIO') {
        return t('message.sharedAudio');
      }
      if (lastMessage.type === 'VIDEO') {
        return t('message.sharedVideo');
      }
      return t('message.attachment');
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

  const typingUsernames = useMemo(
    () => Object.keys(typingUsers).filter((username) => username !== currentUser?.username),
    [currentUser?.username, typingUsers]
  );

  const typingLabel = useMemo(() => {
    if (typingUsernames.length === 0) {
      return '';
    }
    if (typingUsernames.length > 1) {
      return t('typing.many');
    }
    const username = typingUsernames[0];
    const participant = selectedConversation?.participants?.find((user) => user.username === username);
    return t('typing.one', { name: participant?.displayName || username });
  }, [selectedConversation?.participants, t, typingUsernames]);

  const searchPlaceholder = sideView === 'people'
    ? peopleMode === 'contacts' ? t('search.contacts') : t('search.peopleByName')
    : sideView === 'groups' ? t('search.people') : t('search.chats');

  useEffect(() => {
    callStateRef.current = callState;
  }, [callState]);

  useEffect(() => {
    currentUserRef.current = currentUser;
  }, [currentUser]);

  useEffect(() => {
    selectedConversationRef.current = selectedConversation;
  }, [selectedConversation]);

  useEffect(() => {
    if (localVideoRef.current) {
      localVideoRef.current.srcObject = localStream;
    }
  }, [localStream]);

  useEffect(() => {
    if (remoteVideoRef.current) {
      remoteVideoRef.current.srcObject = remoteStream;
    }
    if (remoteAudioRef.current) {
      remoteAudioRef.current.srcObject = remoteStream;
    }
  }, [remoteStream]);

  useEffect(() => () => {
    peerRef.current?.close();
    stopMediaStream(localStreamRef.current);
  }, []);

  useEffect(() => () => {
    window.clearTimeout(typingStopTimeoutRef.current);
    typingClearTimeoutsRef.current.forEach((timeoutId) => window.clearTimeout(timeoutId));
    typingClearTimeoutsRef.current.clear();
  }, []);

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

  const sendTypingState = useCallback((typing, conversationId = selectedConversationRef.current?.id) => {
    const client = stompRef.current;
    if (!conversationId || !client?.connected) {
      typingActiveRef.current = false;
      typingConversationRef.current = null;
      return;
    }
    if (typingActiveRef.current === typing && typingConversationRef.current === conversationId) {
      return;
    }
    typingActiveRef.current = typing;
    typingConversationRef.current = typing ? conversationId : null;
    client.publish({
      destination: '/app/chat.typing',
      body: JSON.stringify({ conversationId, typing })
    });
  }, []);

  const clearRemoteTypingUsers = useCallback(() => {
    typingClearTimeoutsRef.current.forEach((timeoutId) => window.clearTimeout(timeoutId));
    typingClearTimeoutsRef.current.clear();
    setTypingUsers({});
  }, []);

  const removeTypingUser = useCallback((username) => {
    if (!username) {
      return;
    }
    const timeoutId = typingClearTimeoutsRef.current.get(username);
    if (timeoutId) {
      window.clearTimeout(timeoutId);
      typingClearTimeoutsRef.current.delete(username);
    }
    setTypingUsers((current) => {
      if (!current[username]) {
        return current;
      }
      const next = { ...current };
      delete next[username];
      return next;
    });
  }, []);

  const handleTypingEvent = useCallback((event) => {
    const username = event?.username;
    if (!username || username === currentUserRef.current?.username) {
      return;
    }
    if (!event.typing) {
      removeTypingUser(username);
      return;
    }
    setTypingUsers((current) => ({ ...current, [username]: true }));
    const existingTimeout = typingClearTimeoutsRef.current.get(username);
    if (existingTimeout) {
      window.clearTimeout(existingTimeout);
    }
    const timeoutId = window.setTimeout(() => removeTypingUser(username), 2600);
    typingClearTimeoutsRef.current.set(username, timeoutId);
  }, [removeTypingUser]);

  const handleDraftChange = useCallback((event) => {
    const value = event.target.value;
    setDraft(value);
    window.clearTimeout(typingStopTimeoutRef.current);

    if (!value.trim()) {
      sendTypingState(false);
      return;
    }

    sendTypingState(true);
    typingStopTimeoutRef.current = window.setTimeout(() => sendTypingState(false), 1800);
  }, [sendTypingState]);

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

  function selectMedia(event) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) {
      return;
    }
    const validationError = validateMediaFile(file);
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

  async function uploadMediaFile(file) {
    const body = new FormData();
    body.append('file', file);
    return apiFetch('/media', {
      method: 'POST',
      body
    });
  }

  async function uploadSelectedMedia() {
    if (!selectedFile) {
      return null;
    }
    return uploadMediaFile(selectedFile);
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
      const media = profileFile ? await uploadMediaFile(profileFile) : null;
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
    window.clearTimeout(typingStopTimeoutRef.current);
    sendTypingState(false);
    clearRemoteTypingUsers();
    setDraft('');
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
  }, [clearRemoteTypingUsers, sendTypingState]);

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
        removeTypingUser(incoming.sender?.username);
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

    const typingSubscription = stompRef.current.subscribe(
      `/topic/conversations/${selectedConversation.id}/typing`,
      (payload) => {
        handleTypingEvent(JSON.parse(payload.body));
      }
    );

    const callSubscription = stompRef.current.subscribe(
      `/topic/conversations/${selectedConversation.id}/calls`,
      (payload) => {
        handleCallSignal(JSON.parse(payload.body)).catch((exception) => {
          setError(messageFromError(exception));
        });
      }
    );

    return () => {
      messageSubscription.unsubscribe();
      statusSubscription.unsubscribe();
      typingSubscription.unsubscribe();
      callSubscription.unsubscribe();
      sendTypingState(false, selectedConversation.id);
      clearRemoteTypingUsers();
    };
  }, [
    clearRemoteTypingUsers,
    handleTypingEvent,
    loadConversations,
    removeTypingUser,
    selectedConversation,
    sendTypingState,
    socketReady
  ]);

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
    cleanupCall();
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

  function publishCallSignal(type, payload = null, overrides = {}) {
    const conversationId = overrides.conversationId || callStateRef.current.conversationId || selectedConversationRef.current?.id;
    const callId = overrides.callId || callStateRef.current.callId;
    const mode = overrides.mode || callStateRef.current.mode;
    if (!conversationId || !callId || !mode || !stompRef.current?.connected) {
      return false;
    }
    stompRef.current.publish({
      destination: CALL_SIGNAL_DESTINATION,
      body: JSON.stringify({
        conversationId,
        callId,
        type,
        mode,
        payload: payload ? JSON.stringify(payload) : null
      })
    });
    return true;
  }

  function stopMediaStream(stream) {
    stream?.getTracks().forEach((track) => track.stop());
  }

  function resetCallState() {
    setCallState({
      status: 'idle',
      mode: null,
      callId: null,
      conversationId: null,
      incoming: null,
      callerUsername: null
    });
  }

  function cleanupCall() {
    const peer = peerRef.current;
    peerRef.current = null;
    peer?.close();
    stopMediaStream(localStreamRef.current);
    localStreamRef.current = null;
    remoteStreamRef.current = null;
    pendingIceCandidatesRef.current = [];
    setLocalStream(null);
    setRemoteStream(null);
    resetCallState();
  }

  function setupPeerConnection(callId, mode, conversationId, stream) {
    peerRef.current?.close();
    const peer = new RTCPeerConnection({
      iceServers: CALL_ICE_SERVERS
    });
    const remote = new MediaStream();

    peerRef.current = peer;
    localStreamRef.current = stream;
    remoteStreamRef.current = remote;
    setLocalStream(stream);
    setRemoteStream(remote);

    stream.getTracks().forEach((track) => peer.addTrack(track, stream));
    peer.onicecandidate = (event) => {
      if (event.candidate) {
        publishCallSignal('ICE', { candidate: event.candidate }, { callId, mode, conversationId });
      }
    };
    peer.ontrack = (event) => {
      const inboundStream = event.streams[0];
      const tracks = inboundStream?.getTracks() || [event.track];
      tracks.forEach((track) => {
        if (!remote.getTracks().some((existing) => existing.id === track.id)) {
          remote.addTrack(track);
        }
      });
      const nextRemote = new MediaStream(remote.getTracks());
      remoteStreamRef.current = nextRemote;
      setRemoteStream(nextRemote);
    };
    peer.onconnectionstatechange = () => {
      if (['failed', 'closed'].includes(peer.connectionState)) {
        cleanupCall();
      }
    };
    return peer;
  }

  async function addPendingIceCandidates(callId) {
    const peer = peerRef.current;
    if (!peer) {
      return;
    }
    const pending = pendingIceCandidatesRef.current;
    pendingIceCandidatesRef.current = pending.filter((item) => item.callId !== callId);
    for (const item of pending.filter((candidate) => candidate.callId === callId)) {
      try {
        await peer.addIceCandidate(new RTCIceCandidate(item.candidate));
      } catch {}
    }
  }

  async function mediaStreamForCall(mode) {
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error(t('call.unsupported'));
    }
    try {
      return await navigator.mediaDevices.getUserMedia(mediaConstraintsForCall(mode));
    } catch (exception) {
      throw new Error(t(callMediaErrorKey(exception, mode)));
    }
  }

  async function startCall(mode) {
    if (!selectedConversation?.direct) {
      setError(t('call.directOnly'));
      return;
    }
    if (!socketReady || !stompRef.current?.connected) {
      setError(t('connection.reconnecting'));
      return;
    }
    if (callStateRef.current.status !== 'idle') {
      return;
    }

    const callId = createCallId();
    const conversationId = selectedConversation.id;
    try {
      const stream = await mediaStreamForCall(mode);
      const peer = setupPeerConnection(callId, mode, conversationId, stream);
      setCallState({
        status: 'calling',
        mode,
        callId,
        conversationId,
        incoming: null,
        callerUsername: null
      });
      const offer = await peer.createOffer();
      await peer.setLocalDescription(offer);
      publishCallSignal('OFFER', { description: peer.localDescription }, { callId, mode, conversationId });
      setError('');
    } catch (exception) {
      cleanupCall();
      setError(messageFromError(exception));
    }
  }

  async function acceptIncomingCall() {
    const incoming = callStateRef.current.incoming;
    if (!incoming) {
      return;
    }
    const payload = parseCallPayload(incoming);
    try {
      const stream = await mediaStreamForCall(incoming.mode);
      const peer = setupPeerConnection(incoming.callId, incoming.mode, incoming.conversationId, stream);
      setCallState({
        status: 'connecting',
        mode: incoming.mode,
        callId: incoming.callId,
        conversationId: incoming.conversationId,
        incoming: null,
        callerUsername: incoming.senderUsername
      });
      await peer.setRemoteDescription(new RTCSessionDescription(payload.description));
      await addPendingIceCandidates(incoming.callId);
      const answer = await peer.createAnswer();
      await peer.setLocalDescription(answer);
      publishCallSignal(
        'ANSWER',
        { description: peer.localDescription },
        { callId: incoming.callId, mode: incoming.mode, conversationId: incoming.conversationId }
      );
      setCallState((current) => ({ ...current, status: 'in-call' }));
      setError('');
    } catch (exception) {
      cleanupCall();
      setError(messageFromError(exception));
    }
  }

  function rejectIncomingCall() {
    const incoming = callStateRef.current.incoming;
    if (incoming) {
      publishCallSignal('REJECT', null, {
        callId: incoming.callId,
        mode: incoming.mode,
        conversationId: incoming.conversationId
      });
    }
    cleanupCall();
  }

  function endCall() {
    const current = callStateRef.current;
    if (current.status !== 'idle') {
      publishCallSignal('END', null, {
        callId: current.callId,
        mode: current.mode,
        conversationId: current.conversationId
      });
    }
    cleanupCall();
  }

  async function handleCallSignal(signal) {
    if (!signal || signal.senderUsername === currentUserRef.current?.username) {
      return;
    }
    const payload = parseCallPayload(signal);
    const current = callStateRef.current;

    if (signal.type === 'OFFER') {
      if (current.status !== 'idle') {
        publishCallSignal('REJECT', null, {
          callId: signal.callId,
          mode: signal.mode,
          conversationId: signal.conversationId
        });
        return;
      }
      setCallState({
        status: 'incoming',
        mode: signal.mode,
        callId: signal.callId,
        conversationId: signal.conversationId,
        incoming: signal,
        callerUsername: signal.senderUsername
      });
      return;
    }

    if (signal.callId !== current.callId) {
      if (signal.type === 'ICE' && payload.candidate) {
        pendingIceCandidatesRef.current.push({ callId: signal.callId, candidate: payload.candidate });
      }
      return;
    }

    if (signal.type === 'ANSWER' && peerRef.current && payload.description) {
      await peerRef.current.setRemoteDescription(new RTCSessionDescription(payload.description));
      await addPendingIceCandidates(signal.callId);
      setCallState((existing) => ({ ...existing, status: 'in-call' }));
      return;
    }

    if (signal.type === 'ICE' && payload.candidate) {
      if (peerRef.current?.remoteDescription) {
        try {
          await peerRef.current.addIceCandidate(new RTCIceCandidate(payload.candidate));
        } catch {}
      } else {
        pendingIceCandidatesRef.current.push({ callId: signal.callId, candidate: payload.candidate });
      }
      return;
    }

    if (signal.type === 'END' || signal.type === 'REJECT') {
      cleanupCall();
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
      const media = await uploadSelectedMedia();
      const payload = {
        conversationId: selectedConversation.id,
        content,
        type: media ? messageTypeForFile(selectedFile) : 'TEXT',
        assetUrl: null,
        assetKey: media?.objectKey || null,
        expiresInSeconds: expiresInSeconds ? Number(expiresInSeconds) : null
      };

      window.clearTimeout(typingStopTimeoutRef.current);
      sendTypingState(false, selectedConversation.id);
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
              {selectedConversation.direct && (
                <div className="header-call-actions" aria-label={t('call.actions')}>
                  <button
                    className="icon-button"
                    type="button"
                    onClick={() => startCall('AUDIO')}
                    disabled={!socketReady || callState.status !== 'idle'}
                    title={t('call.audio')}
                    aria-label={t('call.audio')}
                  >
                    <Phone size={17} aria-hidden="true" />
                  </button>
                  <button
                    className="icon-button"
                    type="button"
                    onClick={() => startCall('VIDEO')}
                    disabled={!socketReady || callState.status !== 'idle'}
                    title={t('call.video')}
                    aria-label={t('call.video')}
                  >
                    <Video size={17} aria-hidden="true" />
                  </button>
                </div>
              )}
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

            {callState.status !== 'idle' && (
              <section className={`call-panel ${callState.mode === 'VIDEO' ? 'video' : 'audio'}`}>
                <div className="call-copy">
                  <span>{callState.mode === 'VIDEO' ? t('call.video') : t('call.audio')}</span>
                  <strong>
                    {callState.status === 'incoming'
                      ? t('call.incoming', { name: callState.callerUsername || t('conversation.direct') })
                      : callState.status === 'calling'
                        ? t('call.calling')
                        : t('call.active')}
                  </strong>
                </div>
                <div className="call-media">
                  {callState.mode === 'VIDEO' && (
                    <>
                      <video className="remote-video" ref={remoteVideoRef} autoPlay playsInline />
                      <video className="local-video" ref={localVideoRef} autoPlay muted playsInline />
                    </>
                  )}
                  {callState.mode === 'AUDIO' && (
                    <div className="audio-call-visual">
                      <Mic size={22} aria-hidden="true" />
                    </div>
                  )}
                  <audio ref={remoteAudioRef} autoPlay />
                </div>
                <div className="call-controls">
                  {callState.status === 'incoming' && (
                    <button className="call-accept" type="button" onClick={acceptIncomingCall}>
                      <Phone size={17} aria-hidden="true" />
                      {t('call.accept')}
                    </button>
                  )}
                  {callState.status === 'incoming' ? (
                    <button className="call-end" type="button" onClick={rejectIncomingCall}>
                      <PhoneOff size={17} aria-hidden="true" />
                      {t('call.reject')}
                    </button>
                  ) : (
                    <button className="call-end" type="button" onClick={endCall}>
                      <PhoneOff size={17} aria-hidden="true" />
                      {t('call.end')}
                    </button>
                  )}
                </div>
              </section>
            )}

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
                      {!deleted && message.assetUrl && message.type === 'AUDIO' && (
                        <div className="audio-attachment">
                          <FileAudio size={16} aria-hidden="true" />
                          <audio src={message.assetUrl} controls preload="metadata" />
                        </div>
                      )}
                      {!deleted && message.assetUrl && message.type === 'VIDEO' && (
                        <a className="video-attachment" href={message.assetUrl} target="_blank" rel="noreferrer">
                          <video src={message.assetUrl} controls preload="metadata" />
                        </a>
                      )}
                      {!deleted && message.assetUrl && !['IMAGE', 'AUDIO', 'VIDEO'].includes(message.type) && (
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
                {typingLabel && (
                  <div className="typing-indicator" role="status">
                    <span className="typing-dots" aria-hidden="true">
                      <span />
                      <span />
                      <span />
                    </span>
                    {typingLabel}
                  </div>
                )}
              </section>

              <aside className="contact-panel">
                <span className="panel-kicker">{t('contact.info')}</span>
                <div className="contact-card">
                  <ConversationAvatar conversation={selectedConversation} currentUser={currentUser} size="large" />
                  <strong>{conversationLabel(selectedConversation)}</strong>
                  <span>{conversationSubtext(selectedConversation)}</span>
                </div>

                <div className="contact-actions" aria-label={t('contact.actions')}>
                  <button type="button" onClick={() => startCall('AUDIO')} disabled={!selectedConversation.direct || callState.status !== 'idle'}>
                    <Phone size={15} aria-hidden="true" />
                    {t('contact.audio')}
                  </button>
                  <button type="button" onClick={() => startCall('VIDEO')} disabled={!selectedConversation.direct || callState.status !== 'idle'}>
                    <Video size={15} aria-hidden="true" />
                    {t('contact.video')}
                  </button>
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
                  {selectedFile?.type.startsWith('image/') && (
                    <img src={selectedPreview} alt={t('message.selectedUpload')} />
                  )}
                  {selectedFile?.type.startsWith('audio/') && (
                    <div className="selected-media-icon">
                      <FileAudio size={22} aria-hidden="true" />
                    </div>
                  )}
                  {selectedFile?.type.startsWith('video/') && (
                    <video src={selectedPreview} muted playsInline />
                  )}
                  <div>
                    <strong>{selectedFile?.name}</strong>
                    <span>{Math.ceil((selectedFile?.size || 0) / 1024)} KB</span>
                  </div>
                  <button type="button" onClick={clearSelectedFile} title={t('message.removeImage')} aria-label={t('message.removeImage')}>
                    <X size={16} aria-hidden="true" />
                  </button>
                </div>
              )}
              <label className="media-picker" title={t('message.attachMedia')} aria-label={t('message.attachMedia')}>
                <Paperclip size={18} aria-hidden="true" />
                <input
                  type="file"
                  accept={MEDIA_ACCEPT}
                  onChange={selectMedia}
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
                  onChange={handleDraftChange}
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
