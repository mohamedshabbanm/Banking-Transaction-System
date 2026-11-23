import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { get, post } from '../api';
import { useAuth } from '../auth';
import Icon from '../components/Icon';
import { useToast } from '../components/Toast';
import { AmountInput, ErrorCard, Proverb, Sheet, Skeleton, useLoader } from '../components/ui';
import { dateTime, egp, typeLabel } from '../format';
import { play, setSoundOn, soundOn } from '../sound';

export default function Profile() {
  const { user, logout } = useAuth();
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const accounts = useLoader(() => get('/api/accounts'));
  const sms = useLoader(() => get('/api/sms'));
  const [opening, setOpening] = useState(params.get('open') === '1');
  const [sound, setSound] = useState(soundOn());

  const toggleSound = () => {
    const next = !sound;
    setSound(next);
    setSoundOn(next);
    if (next) play('received', { force: true });
  };

  useEffect(() => {
    if (params.get('open')) setParams({}, { replace: true });
  }, [params, setParams]);

  const copy = async (text) => {
    try {
      await navigator.clipboard.writeText(text);
      toast('Account number copied.');
    } catch {
      toast(text);
    }
  };

  return (
    <main className="page">
      <div className="row">
        <div className="tx-icon" style={{ width: 56, height: 56, borderRadius: '50%', background: 'var(--line)', fontWeight: 600, fontSize: 18 }}>
          {user?.fullName?.split(' ').map((p) => p[0]).slice(0, 2).join('')}
        </div>
        <div>
          <h1 style={{ fontSize: 20 }}>{user?.fullName}</h1>
          <p className="small muted">@{user?.username}</p>
        </div>
      </div>

      <div className="between">
        <h2>Your accounts</h2>
        <button type="button" className="btn sm primary" onClick={() => setOpening(true)}><Icon name="plus" size={16} stroke={2.2} /> Open account</button>
      </div>
      {accounts.loading && <Skeleton height={120} />}
      {accounts.error && <ErrorCard error={accounts.error} onRetry={accounts.reload} />}
      {accounts.data?.map((a) => (
        <div key={a.id} className="card stack" style={{ gap: 10 }}>
          <div className="between">
            <h3>{typeLabel[a.type]}</h3>
            <span className={`badge ${a.status === 'ACTIVE' ? 'ok' : 'bad'}`}>{a.status === 'ACTIVE' ? 'Active' : 'Frozen'}</span>
          </div>
          <button type="button" className="between inset" style={{ border: 0, color: 'var(--text)', cursor: 'pointer', width: '100%' }} onClick={() => copy(a.accountNumber)}>
            <span className="num" style={{ letterSpacing: '0.06em' }}>{a.accountNumber.replace(/(\d{4})(?=\d)/g, '$1 ')}</span>
            <Icon name="copy" size={16} color="var(--muted)" />
          </button>
          <div className="between small"><span className="muted">Balance</span><span className="num">{egp(a.balance)}</span></div>
          <p className="tiny muted">
            {a.type === 'SAVINGS' ? '5% yearly interest, paid monthly · 4 withdrawals or transfers a month' : `Overdraft up to ${egp(a.overdraftLimit)} · no monthly limit`}
          </p>
        </div>
      ))}

      <h2>Phone inbox</h2>
      <p className="tiny muted" style={{ marginTop: -12 }}>Simulated SMS: where your one-time codes arrive.</p>
      {sms.data?.length === 0 && <p className="small muted">No messages yet.</p>}
      {sms.data?.slice(0, 3).map((m) => (
        <div key={m.id} className="sms">
          <img className="app-icon" src="/icon.png" alt="" />
          <div className="stack" style={{ gap: 2 }}>
            <p className="tiny muted">{dateTime(m.createdAt)}</p>
            <p className="small">{m.message}</p>
          </div>
        </div>
      ))}

      <div className="card list">
        <Link to="/change-password" className="tx"><div className="tx-icon"><Icon name="key" size={18} /></div><span className="tx-main">Change password</span><Icon name="chevron" size={18} color="var(--muted)" /></Link>
        <button type="button" className="tx" style={{ width: '100%', background: 'none', border: 0, cursor: 'pointer', fontSize: 15, color: 'var(--text)' }} onClick={toggleSound} role="switch" aria-checked={sound}>
          <div className="tx-icon"><Icon name="bell" size={18} /></div>
          <span className="tx-main" style={{ textAlign: 'left' }}>Sounds</span>
          <span className={`badge ${sound ? 'ok' : ''}`}>{sound ? 'On' : 'Off'}</span>
        </button>
        <button type="button" className="tx" style={{ width: '100%', background: 'none', border: 0, cursor: 'pointer', fontSize: 15 }} onClick={logout}>
          <div className="tx-icon"><Icon name="logout" size={18} color="var(--bad)" /></div><span className="tx-main bad" style={{ textAlign: 'left' }}>Log out</span>
        </button>
      </div>
      <Proverb style={{ textAlign: 'center', color: 'var(--muted)', fontSize: 16 }}>حط القرش عالقرش، واحنا نحرسه</Proverb>

      {opening && <OpenAccount onClose={() => setOpening(false)} onDone={accounts.reload} />}
    </main>
  );
}

function OpenAccount({ onClose, onDone }) {
  const toast = useToast();
  const [type, setType] = useState('SAVINGS');
  const [deposit, setDeposit] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (!(Number(deposit) >= 100)) return setError('The first deposit must be at least EGP 100.');
    setBusy(true);
    try {
      const a = await post('/api/accounts', { type, initialDeposit: deposit });
      toast(`Your ${typeLabel[a.type].toLowerCase()} ${a.maskedNumber} is ready.`);
      onDone();
      onClose();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const options = [
    { v: 'SAVINGS', title: 'Savings', ar: 'القرش الأبيض', text: '5% yearly interest. Up to 4 withdrawals or transfers a month. Great for goals.' },
    { v: 'CURRENT', title: 'Current', ar: 'للمصروف اليومي', text: 'For everyday spending. No monthly limit, overdraft up to EGP 2,000.' },
  ];

  return (
    <Sheet title="Open an account" onClose={onClose}>
      <form className="stack-lg" onSubmit={submit} noValidate>
        <fieldset className="stack" style={{ border: 0, padding: 0, margin: 0, gap: 8 }}>
          <legend className="label" style={{ marginBottom: 8 }}>Account type</legend>
          {options.map((o) => (
            <label key={o.v} className="card tight stack" style={{ gap: 4, cursor: 'pointer', borderColor: type === o.v ? 'var(--accent)' : undefined }}>
              <input type="radio" name="type" className="sr-only" checked={type === o.v} onChange={() => setType(o.v)} />
              <div className="between"><h3>{o.title}</h3><Proverb style={{ fontSize: 16 }}>{o.ar}</Proverb></div>
              <p className="small muted">{o.text}</p>
            </label>
          ))}
        </fieldset>
        <div className="field">
          <label htmlFor="deposit">First deposit</label>
          <AmountInput id="deposit" value={deposit} onChange={setDeposit} />
          <p className="hint">At least EGP 100.</p>
        </div>
        {error && <p className="error-text">{error}</p>}
        <button className="btn primary block" disabled={busy}>{busy ? 'Opening…' : 'Open account'}</button>
      </form>
    </Sheet>
  );
}
