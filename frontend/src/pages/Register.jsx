import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { post } from '../api';
import { useAuth } from '../auth';
import Icon from '../components/Icon';
import { Proverb } from '../components/ui';

const rules = [
  { test: (p) => p.length >= 8, label: '8+ characters' },
  { test: (p) => /[A-Z]/.test(p), label: 'Upper case' },
  { test: (p) => /[a-z]/.test(p), label: 'Lower case' },
  { test: (p) => /\d/.test(p), label: 'A number' },
];

export default function Register() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', username: '', password: '' });
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const validate = () => {
    const e = {};
    if (!form.fullName.trim()) e.fullName = 'Enter your full name.';
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.email)) e.email = 'Enter a valid email.';
    if (!/^01[0125]\d{8}$/.test(form.phone.replace(/\s/g, ''))) e.phone = 'Use an Egyptian mobile like 01012345678.';
    if (!/^[a-zA-Z0-9._]{3,30}$/.test(form.username)) e.username = '3-30 letters, numbers, dots or underscores.';
    if (!rules.every((r) => r.test(form.password))) e.password = 'Your password needs all four checks.';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const submit = async (e) => {
    e.preventDefault();
    if (!validate()) return;
    setBusy(true);
    setServerError('');
    try {
      await post('/api/auth/register', { ...form, phone: form.phone.replace(/\s/g, '') });
      await login(form.username, form.password);
      navigate('/profile?open=1');
    } catch (err) {
      setServerError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const field = (key, label, props = {}) => (
    <div className="field">
      <label htmlFor={key}>{label}</label>
      <input id={key} className={`input${errors[key] ? ' invalid' : ''}`} value={form[key]} onChange={set(key)} {...props} />
      {errors[key] && <p className="error-text">{errors[key]}</p>}
    </div>
  );

  return (
    <div className="auth">
      <div className="topbar">
        <Link to="/login" className="icon-btn" aria-label="Back"><Icon name="back" /></Link>
        <img src="/icon.png" alt="" width="36" height="36" />
      </div>
      <div className="stack" style={{ gap: 6 }}>
        <h1>Open your Ershak account</h1>
        <Proverb style={{ textAlign: 'left' }}>القرش الأبيض ينفع في اليوم الأسود</Proverb>
      </div>
      {serverError && <div role="alert" className="alert bad small">{serverError}</div>}
      <form className="stack-lg" onSubmit={submit} noValidate>
        {field('fullName', 'Full name', { autoComplete: 'name', placeholder: 'Omar Ahmed' })}
        {field('email', 'Email', { type: 'email', autoComplete: 'email', placeholder: 'name@example.com' })}
        {field('phone', 'Mobile number', { inputMode: 'tel', autoComplete: 'tel', placeholder: '01012345678' })}
        {field('username', 'Username', { autoCapitalize: 'none', autoComplete: 'username', placeholder: 'omar.ahmed' })}
        <div className="field">
          <label htmlFor="password">Password</label>
          <input id="password" type="password" className={`input${errors.password ? ' invalid' : ''}`}
            autoComplete="new-password" value={form.password} onChange={set('password')} />
          <div className="chips">
            {rules.map((r) => (
              <span key={r.label} className={`badge ${r.test(form.password) ? 'ok' : ''}`}>
                {r.test(form.password) && <Icon name="check" size={12} stroke={2.4} />} {r.label}
              </span>
            ))}
          </div>
          {errors.password && <p className="error-text">{errors.password}</p>}
        </div>
        <button className="btn primary block" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
      </form>
    </div>
  );
}
