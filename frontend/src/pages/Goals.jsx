import { useState } from 'react';
import { del, get, post } from '../api';
import AccountPicker from '../components/AccountPicker';
import Icon from '../components/Icon';
import { useToast } from '../components/Toast';
import { AmountInput, Empty, ErrorCard, Proverb, Sheet, Skeleton, useLoader } from '../components/ui';
import { amount, egp } from '../format';

/** "Ersh 3al ersh": savings goals. */
export default function Goals() {
  const goals = useLoader(() => get('/api/goals'));
  const accounts = useLoader(() => get('/api/accounts'));
  const toast = useToast();
  const [sheet, setSheet] = useState(null);

  const total = (goals.data || []).reduce((s, g) => s + Number(g.savedAmount), 0);
  const refresh = () => {
    goals.reload();
    accounts.reload();
  };

  return (
    <main className="page">
      <div className="between">
        <h1>Savings goals</h1>
        <button type="button" className="btn primary sm" onClick={() => setSheet({ kind: 'new' })} disabled={!accounts.data?.length}>
          <Icon name="plus" size={16} stroke={2.2} /> New goal
        </button>
      </div>

      <div className="card stack" style={{ gap: 6 }}>
        <Proverb style={{ textAlign: 'left' }}>القرش الأبيض ينفع في اليوم الأسود</Proverb>
        <p className="small muted">Total saved in goals</p>
        <p className="num" style={{ fontSize: 28, fontWeight: 700 }}>{egp(total)}</p>
      </div>

      {goals.loading && <Skeleton height={140} />}
      {goals.error && <ErrorCard error={goals.error} onRetry={goals.reload} />}
      {goals.data?.length === 0 && (
        <Empty icon="goal" title="Start your first goal" action={
          <button type="button" className="btn primary" onClick={() => setSheet({ kind: 'new' })} disabled={!accounts.data?.length}>Create a goal</button>}>
          <Proverb>حط القرش عالقرش</Proverb>
          <p className="small muted">A laptop, a trip, an emergency fund. Put money aside and watch it grow.</p>
        </Empty>
      )}

      {goals.data?.map((g) => {
        const done = g.status === 'ACHIEVED';
        return (
          <section key={g.id} className="card stack" style={{ borderColor: done ? 'var(--ok)' : undefined }}>
            <div className="between">
              <div className="row">
                <div className="tx-icon"><Icon name={done ? 'check' : 'goal'} size={20} color={done ? 'var(--ok)' : 'var(--accent)'} stroke={done ? 2.2 : 1.8} /></div>
                <div>
                  <h3>{g.name}</h3>
                  <p className={`tiny ${done ? 'ok' : 'muted'}`}>{done ? 'Goal reached' : g.targetDate ? `By ${new Date(g.targetDate).toLocaleDateString('en-US', { month: 'long', year: 'numeric' })}` : 'No deadline'}</p>
                </div>
              </div>
              <p className={`num ${done ? 'ok' : ''}`} style={{ fontWeight: 600, color: done ? undefined : 'var(--accent)' }}>{g.progress}%</p>
            </div>
            <div className={`progress${done ? ' ok' : ''}`} role="progressbar" aria-valuenow={g.progress} aria-valuemin={0} aria-valuemax={100}>
              <div style={{ width: `${g.progress}%` }} />
            </div>
            <p className="small muted num"><span style={{ color: 'var(--text)', fontWeight: 600 }}>{egp(g.savedAmount)}</span> of {amount(g.targetAmount)}</p>
            {done && <Proverb style={{ textAlign: 'left', fontSize: 17 }}>حطيت القرش عالقرش ووصلت</Proverb>}
            <div className="row" style={{ gap: 8 }}>
              {!done && <button type="button" className="btn sm primary" onClick={() => setSheet({ kind: 'add', goal: g })}>Add money</button>}
              {Number(g.savedAmount) > 0 && <button type="button" className="btn sm" onClick={() => setSheet({ kind: 'out', goal: g })}>Move to account</button>}
              {Number(g.savedAmount) === 0 && (
                <button type="button" className="btn sm danger" onClick={async () => {
                  try { await del(`/api/goals/${g.id}`); toast('Goal deleted.'); refresh(); } catch (err) { toast(err.message, 'bad'); }
                }}>Delete</button>
              )}
            </div>
          </section>
        );
      })}

      {sheet?.kind === 'new' && <NewGoal accounts={accounts.data} onClose={() => setSheet(null)} onDone={refresh} />}
      {(sheet?.kind === 'add' || sheet?.kind === 'out') && (
        <MoveGoal goal={sheet.goal} mode={sheet.kind} onClose={() => setSheet(null)} onDone={refresh}
          account={accounts.data?.find((a) => a.id === sheet.goal.accountId)} />
      )}
    </main>
  );
}

function NewGoal({ accounts, onClose, onDone }) {
  const toast = useToast();
  const [form, setForm] = useState({ name: '', targetAmount: '', targetDate: '', accountId: accounts?.[0]?.id });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (!form.name.trim()) return setError('Give your goal a name.');
    if (!(Number(form.targetAmount) > 0)) return setError('Enter a target amount.');
    setBusy(true);
    try {
      await post('/api/goals', form);
      toast('Goal created. حط القرش عالقرش');
      onDone();
      onClose();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Sheet title="New goal" onClose={onClose}>
      <form className="stack-lg" onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="gname">Name</label>
          <input id="gname" className="input" maxLength={40} placeholder="New laptop" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </div>
        <div className="field">
          <label htmlFor="gtarget">Target</label>
          <AmountInput id="gtarget" value={form.targetAmount} onChange={(v) => setForm({ ...form, targetAmount: v })} />
        </div>
        <div className="field">
          <label htmlFor="gdate">Target date (optional)</label>
          <input id="gdate" type="date" className="input" value={form.targetDate} onChange={(e) => setForm({ ...form, targetDate: e.target.value })} />
        </div>
        <AccountPicker accounts={accounts} value={form.accountId} onChange={(id) => setForm({ ...form, accountId: id })} label="Fund it from" />
        {error && <p className="error-text">{error}</p>}
        <button className="btn primary block" disabled={busy}>{busy ? 'Creating…' : 'Create goal'}</button>
      </form>
    </Sheet>
  );
}

function MoveGoal({ goal, mode, account, onClose, onDone }) {
  const toast = useToast();
  const adding = mode === 'add';
  const [value, setValue] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const remaining = Math.max(0, Number(goal.targetAmount) - Number(goal.savedAmount));

  const submit = async (e) => {
    e.preventDefault();
    if (!(Number(value) > 0)) return setError('Enter an amount.');
    if (!adding && goal.status !== 'ACHIEVED' && !confirming) {
      setConfirming(true);
      return;
    }
    setBusy(true);
    try {
      const g = await post(`/api/goals/${goal.id}/${adding ? 'contribute' : 'withdraw'}`, { amount: value });
      toast(g.status === 'ACHIEVED' && adding ? 'حطيت القرش عالقرش ووصلت' : adding ? 'Saved.' : 'Moved back to your account.');
      onDone();
      onClose();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Sheet title={adding ? `Add to ${goal.name}` : `Move from ${goal.name}`} onClose={onClose}>
      <form className="stack-lg" onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="gamount">Amount</label>
          <AmountInput id="gamount" value={value} onChange={(v) => { setValue(v); setConfirming(false); }} autoFocus />
          <div className="chips">
            {adding && remaining > 0 && <button type="button" className="chip" onClick={() => setValue(String(remaining))}>Finish it: {amount(remaining)}</button>}
            {!adding && <button type="button" className="chip" onClick={() => setValue(String(goal.savedAmount))}>All: {amount(goal.savedAmount)}</button>}
          </div>
          <p className="hint">{adding ? `From ${account?.maskedNumber || 'your account'} · ${egp(Number(account?.balance || 0) - Number(account?.holdAmount || 0))} free` : `Back to ${account?.maskedNumber || 'your account'}`}</p>
        </div>
        {confirming && (
          <div className="alert" role="alert">
            <Icon name="goal" color="var(--warn)" />
            <p className="small">You haven't reached this goal yet. Move the money anyway?</p>
          </div>
        )}
        {error && <p className="error-text">{error}</p>}
        <button className="btn primary block" disabled={busy}>{busy ? 'Working…' : confirming ? 'Yes, move it' : adding ? 'Save' : 'Move to account'}</button>
      </form>
    </Sheet>
  );
}
