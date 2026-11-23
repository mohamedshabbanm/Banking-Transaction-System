import { useState } from 'react';
import { get, post } from '../../api';
import Icon from '../../components/Icon';
import { ErrorCard, Proverb, Skeleton, useLoader } from '../../components/ui';
import { dateTime } from '../../format';

/** The tamper-evident log. Every row carries the hash of the row before it. */
export default function AuditLog() {
  const { data, error, loading, reload } = useLoader(() => get('/api/admin/audit?limit=200'));
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);

  const verify = async () => {
    setBusy(true);
    try {
      setResult(await post('/api/admin/audit/verify'));
      reload();
    } catch (err) {
      setResult({ intact: false, message: err.message });
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <div className="between" style={{ flexWrap: 'wrap', alignItems: 'flex-end' }}>
        <div>
          <h1>Audit log</h1>
          <p className="small muted" style={{ marginTop: 4 }}>Each entry stores SHA-256(previous hash + this entry). Change one row and the chain breaks.</p>
        </div>
        <button type="button" className="btn primary sm" onClick={verify} disabled={busy}>
          <Icon name="shieldCheck" size={16} /> {busy ? 'Checking…' : 'Verify integrity'}
        </button>
      </div>

      {result && (
        <div role="alert" className={`alert ${result.intact ? 'ok' : 'bad'}`}>
          <Icon name={result.intact ? 'shieldCheck' : 'shieldX'} size={22} color={result.intact ? 'var(--ok)' : 'var(--bad)'} />
          <div className="stack" style={{ gap: 2 }}>
            <p style={{ fontWeight: 600 }} className={result.intact ? 'ok' : 'bad'}>{result.intact ? 'Chain intact' : 'Tampering detected'}</p>
            <p className="small">{result.message}</p>
            {!result.intact && <Proverb style={{ textAlign: 'left' }}>المكتوب مكتوب</Proverb>}
          </div>
        </div>
      )}

      {loading && <Skeleton height={300} />}
      {error && <ErrorCard error={error} onRetry={reload} />}
      {data && (
        <section className="card table-wrap">
          <table className="data">
            <thead><tr><th>#</th><th>Time</th><th>Actor</th><th>Action</th><th>Details</th><th>Hash</th></tr></thead>
            <tbody>
              {data.map((e) => (
                <tr key={e.id} style={result && !result.intact && e.id === result.firstBrokenId ? { background: 'var(--bad-bg)' } : undefined}>
                  <td className="muted num">{e.id}</td>
                  <td className="muted" style={{ whiteSpace: 'nowrap' }}>{dateTime(e.timestamp)}</td>
                  <td>{e.actor}</td>
                  <td><span className="badge">{e.action.replaceAll('_', ' ').toLowerCase()}</span></td>
                  <td className="muted" style={{ maxWidth: 320 }}>{e.details}</td>
                  <td className="mono muted" title={e.hash}>{e.hash.slice(0, 10)}…</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </>
  );
}
