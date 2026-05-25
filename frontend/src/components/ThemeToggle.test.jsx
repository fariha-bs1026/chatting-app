import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import ThemeToggle from './ThemeToggle';

describe('ThemeToggle', () => {
  it('shows the next theme action and calls onToggle', async () => {
    const user = userEvent.setup();
    const onToggle = vi.fn();
    render(<ThemeToggle theme="light" onToggle={onToggle} />);

    const button = screen.getByRole('button', { name: 'Switch to dark mode' });
    expect(button).toHaveTextContent('Dark');

    await user.click(button);

    expect(onToggle).toHaveBeenCalledTimes(1);
  });

  it('labels dark mode as switching to white mode', () => {
    render(<ThemeToggle theme="dark" onToggle={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Switch to white mode' })).toHaveTextContent('White');
  });
});
