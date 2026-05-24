import { useState } from 'react';
import { Send } from 'lucide-react';
import { apiFetch } from '../api';
import { messageFromError } from '../errors';
import { translate } from '../i18n';
import { PHONE_PATTERN } from '../validation';
import LanguageSelect from './LanguageSelect';
import LogoMark from './LogoMark';
import ThemeToggle from './ThemeToggle';

function AuthView({ onAuth, theme, onToggleTheme, language, onLanguageChange }) {
  const t = (key, values) => translate(language, key, values);
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
      setError(messageFromError(exception));
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
            <strong>{t('auth.subtitle.register')}</strong>
          </div>
        </div>

        <div className="phone-preview">
          <div className="preview-topbar">
            <div className="avatar small">FA</div>
            <div>
              <strong>Fariha</strong>
              <span>{t('auth.preview.status')}</span>
            </div>
          </div>
          <div className="preview-thread">
            <div className="preview-bubble their-preview">{t('auth.preview.line1')}</div>
            <div className="preview-bubble my-preview">{t('auth.preview.line2')}</div>
            <div className="preview-bubble their-preview">{t('auth.preview.line3')}</div>
          </div>
          <div className="preview-composer">
            <span>{t('message.placeholder')}</span>
            <Send size={15} aria-hidden="true" />
          </div>
        </div>
      </section>

      <form className="auth-panel" onSubmit={submit}>
        <div className="auth-tools">
          <LanguageSelect language={language} onChange={onLanguageChange} />
          <ThemeToggle theme={theme} onToggle={onToggleTheme} />
        </div>

        <div className="brand-row">
          <LogoMark />
          <div>
            <h1>ChatFlow</h1>
            <p>{mode === 'login' ? t('auth.subtitle.login') : t('auth.subtitle.register')}</p>
          </div>
        </div>

        <div className="mode-tabs" role="tablist" aria-label={t('auth.subtitle.register')}>
          <button
            type="button"
            className={mode === 'login' ? 'active' : ''}
            onClick={() => {
              setMode('login');
              setVerification(null);
              setError('');
            }}
          >
            {t('auth.tab.login')}
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
            {t('auth.tab.register')}
          </button>
        </div>

        <label>
          {t('auth.username')}
          <input
            value={form.username}
            onChange={(event) => setForm({ ...form, username: event.target.value })}
            autoComplete="username"
            minLength={3}
            maxLength={50}
            pattern="^[a-zA-Z0-9_.-]{3,50}$"
            title={t('auth.usernameTitle')}
            required
          />
        </label>

        {mode === 'register' && (
          <label>
            {t('auth.displayName')}
            <input
              value={form.displayName}
              onChange={(event) => setForm({ ...form, displayName: event.target.value })}
              autoComplete="name"
              minLength={2}
              maxLength={120}
              required
            />
          </label>
        )}

        {mode === 'register' && (
          <label>
            {t('auth.phoneNumber')}
            <input
              value={form.phoneNumber}
              onChange={(event) => setForm({ ...form, phoneNumber: event.target.value })}
              autoComplete="tel"
              placeholder="+8801712345678"
              pattern={PHONE_PATTERN}
              maxLength={16}
              title={t('auth.phoneTitle')}
              disabled={Boolean(verification)}
              required
            />
          </label>
        )}

        <label>
          {t('auth.password')}
          <input
            value={form.password}
            onChange={(event) => setForm({ ...form, password: event.target.value })}
            type="password"
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            minLength={6}
            maxLength={100}
            disabled={mode === 'register' && Boolean(verification)}
            required
          />
        </label>

        {mode === 'register' && verification && (
          <>
            <div className="verification-note">
              <strong>{t('auth.smsSent')}</strong>
              <span>{t('auth.enterCode', { phone: form.phoneNumber })}</span>
              {verification.debugCode && <code>{t('auth.localCode', { code: verification.debugCode })}</code>}
            </div>
            <label>
              {t('auth.verificationCode')}
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
            ? t('auth.working')
            : mode === 'login'
              ? t('auth.tab.login')
              : verification ? t('auth.verifyCode') : t('auth.sendSmsCode')}
        </button>

        {mode === 'register' && verification && (
          <button className="text-action" type="button" onClick={() => setVerification(null)}>
            {t('auth.changeDetails')}
          </button>
        )}
      </form>
    </main>
  );
}

export default AuthView;
