import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import Icon from '../components/Icon';
import { Proverb } from '../components/ui';
import { time } from '../format';

export default function Login() {
  const { login, user, ready } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: '', password: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [showDemo, setShowDemo] = useState(false);

  if (ready && user) return <Navigate to={user.role === 'ADMIN' ? '/admin' : '/'} replace />;

  const submit = async (e) => {
    e.preventDefault();
    if (!form.username.trim() || !form.password) {
      setError({ message: 'Enter your username and password.' });
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const u = await login(form.username.trim(), form.password);
      navigate(u.mustChangePassword ? '/change-password' : u.role === 'ADMIN' ? '/admin' : '/');
    } catch (err) {
      setError(err);
      setForm((f) => ({ ...f, password: '' }));
    } finally {
      setBusy(false);
    }
  };

  const locked = error?.code === 'USER_LOCKED';

  return (
    <div className="auth">
      <div className="brand" style={{ paddingTop: 12 }}>
        <img src="/logo.png" alt="Ershak" style={{ width: 210, height: 'auto', marginBottom: 6 }} />
        <Proverb>حط القرش عالقرش، واحنا نحرسه</Proverb>
        <p className="small muted">Stack your coins. We guard them.</p>
      </div>

      {error && (
        <div role="alert" className="alert bad">
          <Icon name="lock" color="var(--bad)" style={{ marginTop: 2 }} />
          <div className="stack" style={{ gap: 2 }}>
            {locked && <Proverb lg style={{ textAlign: 'left' }}>التالتة تابتة</Proverb>}
            <p className="small" style={{ color: locked ? 'var(--muted)' : 'var(--text)' }}>
              {locked
                ? `Too many wrong passwords. We locked your login to keep it safe. Try again at ${time(error.body.lockedUntil)}.`
                : error.message}
            </p>
          </div>
        </div>
      )}

      <form className="stack-lg" onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="username">Username</label>
          <input id="username" className="input" autoComplete="username" autoCapitalize="none" spellCheck={false}
            value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input id="password" type="password" className="input" autoComplete="current-password"
            value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </div>
        <button className="btn primary block" disabled={busy}>{busy ? 'Logging in…' : 'Log in'}</button>
      </form>

      <div className="spacer" />

      <div className="stack" style={{ alignItems: 'center', gap: 14 }}>
        <p className="small muted">New here? <Link to="/register" style={{ fontWeight: 600 }}>Open an account</Link></p>
        <button type="button" className="btn ghost sm" onClick={() => setShowDemo(!showDemo)}>
          {showDemo ? 'Hide demo accounts' : 'Try the demo'}
        </button>
        {showDemo && (
          <div className="card tight stack small" style={{ width: '100%', gap: 8 }}>
            <button type="button" className="between btn sm" onClick={() => setForm({ username: 'omar.ahmed', password: 'Ershak@2026' })}>
              <span>Customer</span><span className="muted">omar.ahmed</span>
            </button>
            <button type="button" className="between btn sm" onClick={() => setForm({ username: 'admin', password: 'Admin@2026' })}>
              <span>Bank officer</span><span className="muted">admin</span>
            </button>
          </div>
        )}
        <p className="row tiny muted" style={{ gap: 6 }}>
          <Icon name="shield" size={14} /> Passwords are hashed with PBKDF2. Only you see your data.
        </p>
      </div>
    </div>
  );
}
