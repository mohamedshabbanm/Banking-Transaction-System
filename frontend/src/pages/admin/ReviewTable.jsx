import { useState } from 'react';
import { post } from '../../api';
import { useToast } from '../../components/Toast';
import { amount, dateTime } from '../../format';

/** Held transactions with the reasons the Fraud Guard flagged them, and approve / reject actions. */
export default function ReviewTable({ items, onChange }) {
  const toast = useToast();
  const [busy, setBusy] = useState(null);

  const decide = async (id, action) => {
    setBusy(id);
    try {
      const r = await post(`/api/admin/reviews/${id}/${action}`);
      toast(r.message, action === 'approve' ? 'ok' : 'bad');
      onChange();
    } catch (err) {
      toast(err.message, 'bad');
    } finally {
      setBusy(null);
    }
  };

  if (!items.length) {
    return <p className="small muted" style={{ padding: '12px 0' }}>Nothing waiting. Every transfer is either sent or stopped.</p>;
  }

  return (
    <div className="table-wrap">
      <table className="data">
        <thead>
          <tr><th>Customer</th><th>Amount</th><th>Risk</th><th>Why</th><th style={{ textAlign: 'right' }}>Decision</th></tr>
        </thead>
        <tbody>
          {items.map((r) => (
            <tr key={r.transactionId}>
              <td>
                <p style={{ fontWeight: 500 }}>{r.customerName}</p>
                <p className="tiny muted">{dateTime(r.createdAt)} · {r.reference}</p>
              </td>
              <td className="num" style={{ fontWeight: 600, whiteSpace: 'nowrap' }}>
                {amount(r.amount)}
                <p className="tiny muted" style={{ fontWeight: 400 }}>{r.type === 'WITHDRAW' ? 'Withdrawal' : `${r.fromAccount} → ${r.toAccount}`}</p>
              </td>
              <td><span className="badge bad">{r.score}</span></td>
              <td className="muted" style={{ maxWidth: 280 }}>{r.reasons.map((x) => x.reason).join(' · ')}</td>
              <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                <button type="button" className="btn sm danger" disabled={busy === r.transactionId} onClick={() => decide(r.transactionId, 'reject')}>Reject</button>{' '}
                <button type="button" className="btn sm primary" disabled={busy === r.transactionId} onClick={() => decide(r.transactionId, 'approve')}>Approve</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
