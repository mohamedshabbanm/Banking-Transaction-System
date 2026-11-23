import { Link, useNavigate } from 'react-router-dom';
import { get } from '../api';
import { useAuth } from '../auth';
import Icon from '../components/Icon';
import { Empty, ErrorCard, Proverb, Skeleton, TxRow, useLoader } from '../components/ui';
import { amount, firstName, greeting, typeLabel } from '../format';

export default function Home() {
  const { user, unread } = useAuth();
  const navigate = useNavigate();
  const accounts = useLoader(() => get('/api/accounts'));
  const recent = useLoader(() => get('/api/transactions/recent?limit=6'));

  return (
    <main className="page">
      <div className="between">
        <div className="row">
          <img src="/icon.png" alt="Ershak" width="44" height="44" />
          <div>
            <p className="small muted">{greeting()}</p>
            <p style={{ fontSize: 18, fontWeight: 600 }}>{firstName(user?.fullName)}</p>
          </div>
        </div>
        <button type="button" className="icon-btn" aria-label={`Notifications${unread ? `, ${unread} new` : ''}`}
          onClick={() => navigate('/notifications')}>
          <Icon name="bell" />
          {unread > 0 && <span className="dot" />}
        </button>
      </div>

      {accounts.loading && <Skeleton height={190} />}
      {accounts.error && <ErrorCard error={accounts.error} onRetry={accounts.reload} />}
      {accounts.data && accounts.data.length === 0 && (
        <Empty title="Open your first account" action={<Link className="btn primary" to="/profile?open=1">Open account</Link>}>
          <Proverb>القرش الأبيض ينفع في اليوم الأسود</Proverb>
          <p className="small muted">Choose Savings to grow your money or Current for everyday spending.</p>
        </Empty>
      )}
      {accounts.data && accounts.data.length > 0 && (
        <div className="balance-cards">
          {accounts.data.map((a) => (
            <section key={a.id} className="card balance stack" aria-label={typeLabel[a.type]} style={{ gap: 14 }}>
              <div className="between">
                <p className="small muted">{typeLabel[a.type]}</p>
                <div className="row" style={{ gap: 8 }}>
                  {a.status === 'FROZEN' && <span className="badge bad">Frozen</span>}
                  <p className="small muted num">{a.maskedNumber}</p>
                </div>
              </div>
              <div>
                <p className="tiny muted">Available balance</p>
                <p className="row" style={{ alignItems: 'baseline', gap: 8 }}>
                  <span className="muted" style={{ fontWeight: 500 }}>EGP</span>
                  <span className="amount">{amount(a.availableBalance)}</span>
                </p>
                {Number(a.holdAmount) > 0 && <p className="tiny warn">EGP {amount(a.holdAmount)} held for review</p>}
                {a.type === 'CURRENT' && Number(a.balance) < 0 && <p className="tiny warn">Using overdraft: EGP {amount(-a.balance)}</p>}
              </div>
              <div className="row" style={{ borderTop: '1px solid var(--line)', paddingTop: 12, gap: 10 }}>
                <Icon name="shieldCheck" size={18} color="var(--accent)" />
                <p className="small">Fraud Guard is on</p>
                <p className="tiny muted swipe-hint" style={{ marginLeft: 'auto' }}>{accounts.data.length > 1 ? 'Swipe for more' : 'Checks every transfer'}</p>
              </div>
            </section>
          ))}
        </div>
      )}

      <div className="grid-4">
        <button type="button" className="action primary" onClick={() => navigate('/transfer')}><Icon name="transfer" size={22} />Transfer</button>
        <button type="button" className="action" onClick={() => navigate('/move/deposit')}><Icon name="plus" size={22} />Deposit</button>
        <button type="button" className="action" onClick={() => navigate('/move/withdraw')}><Icon name="cash" size={22} />Withdraw</button>
        <button type="button" className="action" onClick={() => navigate('/goals')}><Icon name="goal" size={22} />Goals</button>
      </div>

      <div className="between">
        <h2>Recent activity</h2>
        <Link to="/activity" className="small">See all</Link>
      </div>
      {recent.loading && <Skeleton height={200} />}
      {recent.data && recent.data.length === 0 && <p className="small muted">No transactions yet.</p>}
      {recent.data && recent.data.length > 0 && (
        <div className="card list">
          {recent.data.map((tx) => <TxRow key={tx.id} tx={tx} />)}
        </div>
      )}
    </main>
  );
}
