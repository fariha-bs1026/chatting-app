import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import StatusIcon from './StatusIcon';

describe('StatusIcon', () => {
  it('renders a sent check by default', () => {
    const { container } = render(<StatusIcon status="SENT" />);

    expect(container.querySelector('.status-icon.sent')).toBeInTheDocument();
  });

  it('renders delivered and read double-check states', () => {
    const delivered = render(<StatusIcon status="DELIVERED" />);
    expect(delivered.container.querySelector('.status-icon.delivered')).toBeInTheDocument();
    delivered.unmount();

    const read = render(<StatusIcon status="READ" />);
    expect(read.container.querySelector('.status-icon.read')).toBeInTheDocument();
  });
});
