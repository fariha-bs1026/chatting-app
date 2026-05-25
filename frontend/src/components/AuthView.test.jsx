import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiFetch } from '../api';
import AuthView from './AuthView';

vi.mock('../api', () => ({
  apiFetch: vi.fn()
}));

function renderAuthView(props = {}) {
  return render(
    <AuthView
      onAuth={props.onAuth || vi.fn()}
      theme="light"
      onToggleTheme={vi.fn()}
      language="en"
      onLanguageChange={vi.fn()}
    />
  );
}

beforeEach(() => {
  apiFetch.mockReset();
});

describe('AuthView', () => {
  it('submits login credentials through the central API helper', async () => {
    const user = userEvent.setup();
    const onAuth = vi.fn();
    apiFetch.mockResolvedValue({ user: { id: 'alice-id', username: 'alice' } });
    renderAuthView({ onAuth });

    await user.type(screen.getByLabelText('Username'), 'alice');
    await user.type(screen.getByLabelText('Password'), 'secret1');
    await user.click(screen.getAllByRole('button', { name: 'Login' }).at(-1));

    await waitFor(() => {
      expect(apiFetch).toHaveBeenCalledWith('/auth/login', {
        method: 'POST',
        body: { username: 'alice', password: 'secret1' }
      });
      expect(onAuth).toHaveBeenCalledWith({ user: { id: 'alice-id', username: 'alice' } });
    });
  });

  it('starts SMS registration and shows the local debug code', async () => {
    const user = userEvent.setup();
    apiFetch.mockResolvedValue({ verificationId: 'verify-1', debugCode: '123456' });
    renderAuthView();

    await user.click(screen.getByRole('button', { name: 'Register' }));
    await user.type(screen.getByLabelText('Username'), 'alice');
    await user.type(screen.getByLabelText('Display name'), 'Alice');
    await user.type(screen.getByLabelText('Phone number'), '+8801712345678');
    await user.type(screen.getByLabelText('Password'), 'secret1');
    await user.click(screen.getByRole('button', { name: 'Send SMS code' }));

    expect(await screen.findByText('Local test code: 123456')).toBeInTheDocument();
    expect(apiFetch).toHaveBeenCalledWith('/auth/register/start', {
      method: 'POST',
      body: {
        username: 'alice',
        displayName: 'Alice',
        phoneNumber: '+8801712345678',
        password: 'secret1'
      }
    });
  });
});
