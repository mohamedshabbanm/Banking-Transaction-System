import { useEffect, useState } from 'react';
import { download, get } from '../api';
import Icon from '../components/Icon';
import { ErrorCard, Proverb, Skeleton, TxRow, useLoader } from '../components/ui';
import { groupByDay, typeLabel } from '../format';
import { useToast } from '../components/Toast';

const TYPES = [
  ['', 'All'], ['TRANSFER', 'Transfers'], ['DEPOSIT', 'Deposits'], ['WITHDRAW', 'Withdrawals'],
  ['GOAL_DEPOSIT', 'Goals'], ['INTEREST', 'Interest'],
];

/** "Elly leek w elly 3aleek": transaction history with filters and PDF / CSV statements. */
export default function Activity() {
  const accounts = useLoader(() => get('/api/accounts'));
  const toast = useToast();
  const [accountId, setAccountId] = useState(null);
  const [filters, setFilters] = useState({ type: '', status: '', from: '', to: '' });
  const [page, setPage] = useState(1);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (accounts.data?.length && !accountId) setAccountId(accounts.data[0].id);
  }, [accounts.data, accountId]);

  useEffect(() => {
    if (!accountId) return;
    const q = new URLSearchParams({ page, size: 15, ...Object.fromEntries(Object.entries(filters).filter(([, v]) => v)) });
    setData(null);
    get(`/api/accounts/${accountId}/transactions?${q}`).then(setData).catch(setError);
  }, [accountId, filters, page]);

  const setFilter = (k, v) => {
    setPage(1);
    setFilters({ ...filters, [k]: v });
  };

  const exportStatement = async (format) => {
    try {
      const q = new URLSearchParams(Object.fromEntries(['from', 'to'].filter((k) => filters[k]).map((k) => [k, filters[k]])));
      await download(`/api/accounts/${accountId}/statement.${format}?${q}`, `ershak-statement.${format}`);
    } catch (err) {
      toast(err.message, 'bad');
    }
  };

  return (
    <main className="page">
      <div className="between">
        <div>
          <h1>Activity</h1>
          <Proverb style={{ fontSize: 16, textAlign: 'left' }}>اللي ليك واللي عليك</Proverb>
        </div>
        <div className="row" style={{ gap: 8 }}>
          <button type="button" className="btn sm" onClick={() => exportStatement('pdf')} disabled={!accountId}>
            <Icon name="download" size={16} /> PDF
          </button>
          <button type="button" className="btn sm" onClick={() => exportStatement('csv')} disabled={!accountId}>
            <Icon name="download" size={16} /> CSV
          </button>
        </div>
      </div>

      {accounts.loading && <Skeleton height={44} />}
      {accounts.error && <ErrorCard error={accounts.error} onRetry={accounts.reload} />}
      {accounts.data?.length > 1 && (
        <div className="chips" role="tablist" aria-label="Account">
          {accounts.data.map((a) => (
            <button key={a.id} type="button" role="tab" aria-selected={a.id === accountId}
              className={`chip${a.id === accountId ? ' on' : ''}`} onClick={() => { setAccountId(a.id); setPage(1); }}>
              {typeLabel[a.type].replace(' account', '')} {a.maskedNumber}
            </button>
          ))}
        </div>
      )}

      <div className="stack" style={{ gap: 10 }}>
        <div className="chips">
          {TYPES.map(([v, l]) => (
            <button key={v} type="button" className={`chip${filters.type === v ? ' on' : ''}`} onClick={() => setFilter('type', v)}>{l}</button>
          ))}
        </div>
        <div className="grid-2">
          <div className="field">
            <label htmlFor="from" className="tiny muted">From</label>
            <input id="from" type="date" className="input" style={{ height: 42 }} value={filters.from} onChange={(e) => setFilter('from', e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="to" className="tiny muted">To</label>
            <input id="to" type="date" className="input" style={{ height: 42 }} value={filters.to} onChange={(e) => setFilter('to', e.target.value)} />
          </div>
        </div>
        <select aria-label="Status" className="input" style={{ height: 42 }} value={filters.status} onChange={(e) => setFilter('status', e.target.value)}>
          <option value="">Any status</option>
          <option value="COMPLETED">Completed</option>
          <option value="PENDING_OTP">Waiting for code</option>
          <option value="PENDING_REVIEW">Under review</option>
          <option value="REJECTED">Cancelled</option>
        </select>
      </div>

      {error && <ErrorCard error={error} />}
      {!data && accountId && <Skeleton height={300} />}
      {data && data.items.length === 0 && <p className="small muted" style={{ textAlign: 'center', padding: 24 }}>No transactions match these filters.</p>}
      {data && groupByDay(data.items).map((g) => (
        <section key={g.label} className="stack" style={{ gap: 8 }}>
          <h3 className="small muted">{g.label}</h3>
          <div className="card list">{g.items.map((tx) => <TxRow key={tx.id} tx={tx} />)}</div>
        </section>
      ))}
      {data && data.totalPages > 1 && (
        <div className="between">
          <button type="button" className="btn sm" disabled={page <= 1} onClick={() => setPage(page - 1)}>Previous</button>
          <span className="small muted">Page {data.page} of {data.totalPages}</span>
          <button type="button" className="btn sm" disabled={page >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
        </div>
      )}
    </main>
  );
}
