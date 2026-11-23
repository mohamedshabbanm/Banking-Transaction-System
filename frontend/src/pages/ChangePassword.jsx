import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { post } from '../api';
import { useAuth } from '../auth';
import { useToast } from '../components/Toast';
import { Proverb } from '../components/ui';

/** Forced on first login for the default officer account; also reachable from Profile. */
export default function ChangePassword() {
  const { user, ready, refresh } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (ready && !user) return <Navigate to="/login" replace />;

  const submit = async (e) => {
    e.preventDefault();
    if (form.newPassword !== form.confirm) {
      setError("The new passwords don't match.");
      return;
    }
    setBusy(true);
    setError('');
    try {
      await post('/api/me/password', { currentPassword: form.currentPassword, newPassword: form.newPassword });
      const u = await refresh();
      toast('Password changed.');
      navigate(u?.role === 'ADMIN' ? '/admin' : '/profile');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth">
      <div className="stack" style={{ gap: 6 }}>
        <img src="/icon.png" alt="" width="44" height="44" />
        <h1>{user?.mustChangePassword ? 'Set your own password' : 'Change password'}</h1>
        {user?.mustChangePassword && <p className="small muted">This account still uses the default password. Choose a new one to continue.</p>}
        <Proverb style={{ textAlign: 'left' }}>الحرص واجب</Proverb>
      </div>
      {error && <div role="alert" className="alert bad small">{error}</div>}
      <form className="stack-lg" onSubmit={submit}>
        <div className="field">
          <label htmlFor="current">Current password</label>
          <input id="current" type="password" className="input" autoComplete="current-password"
            value={form.currentPassword} onChange={(e) => setForm({ ...form, currentPassword: e.target.value })} />
        </div>
        <div className="field">
          <label htmlFor="new">New password</label>
          <input id="new" type="password" className="input" autoComplete="new-password"
            value={form.newPassword} onChange={(e) => setForm({ ...form, newPassword: e.target.value })} />
          <p className="hint">8+ characters with upper case, lower case and a number.</p>
        </div>
        <div className="field">
          <label htmlFor="confirm">Repeat new password</label>
          <input id="confirm" type="password" className="input" autoComplete="new-password"
            value={form.confirm} onChange={(e) => setForm({ ...form, confirm: e.target.value })} />
        </div>
        <button className="btn primary block" disabled={busy}>{busy ? 'Saving…' : 'Save password'}</button>
      </form>
    </div>
  );
}
