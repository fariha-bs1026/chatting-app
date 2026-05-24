import { Users } from 'lucide-react';
import { conversationTitle, directParticipant } from '../conversationUtils';
import UserAvatar from './UserAvatar';

function ConversationAvatar({ conversation, currentUser, size = '' }) {
  if (conversation?.direct) {
    return <UserAvatar user={directParticipant(conversation, currentUser)} name={conversationTitle(conversation, currentUser)} size={size} />;
  }
  return (
    <div className={`avatar ${size}`.trim()}>
      <Users size={size === 'large' ? 28 : size === 'small' ? 17 : 22} aria-hidden="true" />
    </div>
  );
}

export default ConversationAvatar;
