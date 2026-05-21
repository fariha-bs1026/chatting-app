import { useState } from 'react';
import { Send } from 'lucide-react';
import { apiFetch } from '../api';
import LogoMark from './LogoMark';
import ThemeToggle from './ThemeToggle';

function AuthView({ onAuth, theme, onToggleTheme }) {
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({
    username: '',
    displayName: '',
    phoneNumber: '',
    verificationCode: '',
    password: ''
  });
  const [verification, setVerification] = useState(null);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      if (mode === 'login') {
        const data = await apiFetch('/auth/login', {
          method: 'POST',
          body: { username: form.username, password: form.password }
        });
        onAuth(data);
        return;
      }

      if (!verification) {
        const data = await apiFetch('/auth/register/start', {
          method: 'POST',
          body: {
            username: form.username,
            displayName: form.displayName,
            phoneNumber: form.phoneNumber,
            password: form.password
          }
        });
        setVerification(data);
        return;
      }

      const data = await apiFetch('/auth/register/verify', {
        method: 'POST',
        body: {
          verificationId: verification.verificationId,
          code: form.verificationCode
        }
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
          <button
            type="button"
            className={mode === 'login' ? 'active' : ''}
            onClick={() => {
              setMode('login');
              setVerification(null);
              setError('');
            }}
          >
            Login
          </button>
          <button
            type="button"
            className={mode === 'register' ? 'active' : ''}
            onClick={() => {
              setMode('register');
              setVerification(null);
              setError('');
            }}
          >
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

        {mode === 'register' && (
          <label>
            Phone number
            <input
              value={form.phoneNumber}
              onChange={(event) => setForm({ ...form, phoneNumber: event.target.value })}
              autoComplete="tel"
              placeholder="+8801712345678"
              pattern="^\+[1-9]\d{7,14}$"
              title="Use international E.164 format, for example +8801712345678"
              disabled={Boolean(verification)}
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
            disabled={mode === 'register' && Boolean(verification)}
            required
          />
        </label>

        {mode === 'register' && verification && (
          <>
            <div className="verification-note">
              <strong>SMS code sent</strong>
              <span>Enter the 6-digit code sent to {form.phoneNumber}.</span>
              {verification.debugCode && <code>Local test code: {verification.debugCode}</code>}
            </div>
            <label>
              Verification code
              <input
                value={form.verificationCode}
                onChange={(event) => setForm({ ...form, verificationCode: event.target.value })}
                autoComplete="one-time-code"
                inputMode="numeric"
                pattern="\d{6}"
                maxLength={6}
                required
              />
            </label>
          </>
        )}

        {error && <p className="form-error">{error}</p>}

        <button className="primary-action" type="submit" disabled={submitting}>
          <LogoMark size="tiny" />
          {submitting
            ? 'Working...'
            : mode === 'login'
              ? 'Login'
              : verification ? 'Verify code' : 'Send SMS code'}
        </button>

        {mode === 'register' && verification && (
          <button className="text-action" type="button" onClick={() => setVerification(null)}>
            Change details
          </button>
        )}
      </form>
    </main>
  );
}

export default AuthView;
