import { useEffect, useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { get, post } from '../api';
import AccountPicker from '../components/AccountPicker';
import Outcome from '../components/Outcome';
import { AmountInput, ErrorCard, Skeleton, TopBar, useLoader } from '../components/ui';
import { egp } from '../format';

const QUICK = ['500', '1000', '2000', '5000'];

/** Deposit or withdraw cash. Withdrawals go through the Fraud Guard too. */
export default function Move() {
  const { mode } = useParams();
  const deposit = mode === 'deposit';
  const accounts = useLoader(() => get('/api/accounts'));
  const [accountId, setAccountId] = useState(null);
  const [value, setValue] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);

  useEffect(() => {
    const first = accounts.data?.find((a) => a.status === 'ACTIVE');
    if (first && !accountId) setAccountId(first.id);
  }, [accounts.data, accountId]);

  if (mode !== 'deposit' && mode !== 'withdraw') return <Navigate to="/" replace />;
  if (result) return <main className="page narrow"><Outcome result={result} title={deposit ? 'Deposit' : 'Withdrawal'} /></main>;

  const account = accounts.data?.find((a) => a.id === accountId);

  const submit = async (e) => {
    e.preventDefault();
    if (!account) return setError('Choose an account.');
    if (!(Number(value) > 0)) return setError('Enter an amount.');
    if (!deposit && Number(value) > Number(account.availableBalance)) {
      return setError(`Not enough balance. Available: ${egp(account.availableBalance)}`);
    }
    setBusy(true);
    setError('');
    try {
      setResult(await post(`/api/transactions/${mode}`, { accountId, amount: value }));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="page narrow">
      <TopBar title={deposit ? 'Deposit' : 'Withdraw'} />
      {accounts.loading && <Skeleton height={260} />}
      {accounts.error && <ErrorCard error={accounts.error} onRetry={accounts.reload} />}
      {accounts.data && (
        <form className="stack-lg" onSubmit={submit} noValidate>
          <AccountPicker accounts={accounts.data} value={accountId} onChange={setAccountId} label={deposit ? 'To' : 'From'} />
          <div className="field">
            <label htmlFor="amount">Amount</label>
            <AmountInput id="amount" value={value} invalid={!!error} onChange={setValue} autoFocus />
            <div className="chips">
              {QUICK.map((q) => (
                <button key={q} type="button" className={`chip${value === q ? ' on' : ''}`} onClick={() => setValue(q)}>{Number(q).toLocaleString()}</button>
              ))}
            </div>
            {error ? <p className="error-text">{error}</p>
              : <p className="hint">{deposit ? 'Up to EGP 50,000 per deposit.' : 'Up to EGP 10,000 per day. Savings accounts allow 4 withdrawals a month.'}</p>}
          </div>
          <button className="btn primary block" disabled={busy}>
            {busy ? 'Working…' : `${deposit ? 'Deposit' : 'Withdraw'}${Number(value) > 0 ? ` ${egp(value)}` : ''}`}
          </button>
        </form>
      )}
    </main>
  );
}
