import { initials } from '../conversationUtils';

function UserAvatar({ user, name, size = '' }) {
  const label = name || user?.displayName || '?';
  return (
    <div className={`avatar ${size}`.trim()}>
      {user?.avatarUrl ? <img src={user.avatarUrl} alt="" /> : initials(label)}
    </div>
  );
}

export default UserAvatar;
