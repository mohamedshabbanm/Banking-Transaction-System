import { useEffect, useState } from 'react';
import { get, post } from '../../api';
import Icon from '../../components/Icon';
import { useToast } from '../../components/Toast';
import { ErrorCard, Skeleton } from '../../components/ui';
import { egp, typeLabel } from '../../format';

export default function Customers() {
  const toast = useToast();
  const [q, setQ] = useState('');
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [open, setOpen] = useState(null);

  const load = (query = q) => get(`/api/admin/customers?q=${encodeURIComponent(query)}`).then(setData).catch(setError);

  useEffect(() => {
    const t = setTimeout(() => load(q), 250);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q]);

  const act = async (path, message) => {
    try {
      await post(path);
      toast(message);
      load();
    } catch (err) {
      toast(err.message, 'bad');
    }
  };

  return (
    <>
      <div className="between" style={{ flexWrap: 'wrap' }}>
        <h1>Customers</h1>
        <label className="row input" style={{ maxWidth: 320, height: 42, gap: 8 }}>
          <Icon name="search" size={16} color="var(--muted)" />
          <span className="sr-only">Search customers</span>
          <input type="search" placeholder="Name, username, phone or account" value={q} onChange={(e) => setQ(e.target.value)}
            style={{ border: 0, background: 'transparent', color: 'var(--text)', outline: 'none', width: '100%', fontSize: 14, fontFamily: 'inherit' }} />
        </label>
      </div>
      {error && <ErrorCard error={error} />}
      {!data && !error && <Skeleton height={240} />}
      {data?.length === 0 && <p className="small muted">No customers match.</p>}
      <div className="stack">
        {data?.map((c) => (
          <section key={c.id} className="card stack" style={{ gap: 12 }}>
            <button type="button" className="between" onClick={() => setOpen(open === c.id ? null : c.id)}
              style={{ background: 'none', border: 0, color: 'var(--text)', padding: 0, cursor: 'pointer', textAlign: 'left' }} aria-expanded={open === c.id}>
              <div className="row">
                <div className="tx-icon" style={{ borderRadius: '50%', fontWeight: 600, fontSize: 13 }}>{c.fullName.split(' ').map((p) => p[0]).slice(0, 2).join('')}</div>
                <div>
                  <p style={{ fontWeight: 600 }}>{c.fullName}</p>
                  <p className="tiny muted">@{c.username} · {c.phone} · {c.accounts.length} account{c.accounts.length === 1 ? '' : 's'}</p>
                </div>
              </div>
              <div className="row" style={{ gap: 8 }}>
                {c.locked && <span className="badge bad"><Icon name="lock" size={12} />Login locked</span>}
                <Icon name="chevron" size={18} color="var(--muted)" style={{ transform: open === c.id ? 'rotate(90deg)' : undefined, transition: 'transform .15s' }} />
              </div>
            </button>
            {open === c.id && (
              <div className="stack">
                <hr className="divider" />
                {c.accounts.map((a) => (
                  <div key={a.id} className="inset between" style={{ flexWrap: 'wrap' }}>
                    <div>
                      <p style={{ fontWeight: 500 }}>{typeLabel[a.type]} <span className="num muted small">{a.accountNumber}</span></p>
                      <p className="tiny muted num">Balance {egp(a.balance)}{Number(a.holdAmount) > 0 ? ` · held ${egp(a.holdAmount)}` : ''}</p>
                    </div>
                    <div className="row" style={{ gap: 8 }}>
                      <span className={`badge ${a.status === 'ACTIVE' ? 'ok' : 'bad'}`}>{a.status === 'ACTIVE' ? 'Active' : 'Frozen'}</span>
                      {a.status === 'ACTIVE'
                        ? <button type="button" className="btn sm danger" onClick={() => act(`/api/admin/accounts/${a.id}/freeze`, 'Account frozen.')}><Icon name="snow" size={14} />Freeze</button>
                        : <button type="button" className="btn sm" onClick={() => act(`/api/admin/accounts/${a.id}/unfreeze`, 'Account active again.')}>Unfreeze</button>}
                    </div>
                  </div>
                ))}
                <div className="row" style={{ gap: 8 }}>
                  {c.locked
                    ? <button type="button" className="btn sm" onClick={() => act(`/api/admin/users/${c.id}/unlock`, 'Login unlocked.')}>Unlock login</button>
                    : <button type="button" className="btn sm danger" onClick={() => act(`/api/admin/users/${c.id}/lock`, 'Login locked.')}><Icon name="lock" size={14} />Lock login</button>}
                  <span className="tiny muted">{c.email}</span>
                </div>
              </div>
            )}
          </section>
        ))}
      </div>
    </>
  );
}
