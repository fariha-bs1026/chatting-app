import { Check, CheckCheck } from 'lucide-react';

function StatusIcon({ status }) {
  if (status === 'READ') {
    return <CheckCheck className="status-icon read" size={15} aria-hidden="true" />;
  }
  if (status === 'DELIVERED') {
    return <CheckCheck className="status-icon delivered" size={15} aria-hidden="true" />;
  }
  return <Check className="status-icon sent" size={15} aria-hidden="true" />;
}

export default StatusIcon;
