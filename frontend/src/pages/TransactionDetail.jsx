import { useParams } from 'react-router-dom';
import { get } from '../api';
import { Held, OtpPanel, Reasons } from '../components/Outcome';
import { ErrorCard, Skeleton, StatusBadge, TopBar, typeName, useLoader } from '../components/ui';
import { dateTime, egp } from '../format';

export default function TransactionDetail() {
  const { id } = useParams();
  const { data, error, loading, reload } = useLoader(() => get(`/api/transactions/${id}`), [id]);
  const tx = data?.transaction;
  const out = tx?.direction === 'OUT';

  if (tx && out && tx.status === 'PENDING_OTP') {
    return (
      <main className="page narrow">
        <TopBar title="Finish transfer" />
        <OtpPanel tx={tx} reasons={data.risk?.reasons} score={data.risk?.score} onFinish={() => reload()} />
      </main>
    );
  }
  if (tx && out && tx.status === 'PENDING_REVIEW') {
    return (
      <main className="page narrow">
        <TopBar title="Transfer details" />
        <Held tx={tx} reasons={data.risk?.reasons} score={data.risk?.score} />
      </main>
    );
  }

  return (
    <main className="page narrow">
      <TopBar title="Transaction" />
      {loading && <Skeleton height={320} />}
      {error && <ErrorCard error={error} />}
      {tx && (
        <>
          <div className="stack" style={{ alignItems: 'center', gap: 6, padding: '12px 0' }}>
            <p className="small muted">{tx.title}</p>
            <p className={`num ${out ? '' : 'ok'}`} style={{ fontSize: 34, fontWeight: 700, textDecoration: tx.status === 'REJECTED' ? 'line-through' : undefined }}>
              {out ? '-' : '+'}{egp(tx.amount)}
            </p>
            <StatusBadge status={tx.status} />
          </div>
          <div className="card stack" style={{ gap: 12 }}>
            <Row k="Type" v={typeName(tx)} />
            {data.fromAccount && <Row k="From" v={data.fromAccount} />}
            {data.toAccount && <Row k="To" v={data.toAccount} />}
            {out && Number(tx.fee) > 0 && <Row k="Fee" v={egp(tx.fee)} />}
            {tx.note && tx.type === 'TRANSFER' && <Row k="Note" v={tx.note} />}
            <Row k="Date" v={dateTime(tx.createdAt)} />
            <Row k="Reference" v={tx.reference} />
          </div>
          {data.risk && data.risk.reasons.length > 0 && <Reasons reasons={data.risk.reasons} score={data.risk.score} />}
          {data.risk && data.risk.reasons.length === 0 && out && (
            <p className="tiny muted" style={{ textAlign: 'center' }}>Fraud Guard checked this transfer: low risk.</p>
          )}
        </>
      )}
    </main>
  );
}

function Row({ k, v }) {
  return (
    <div className="between small">
      <span className="muted">{k}</span>
      <span className="num" style={{ textAlign: 'right' }}>{v}</span>
    </div>
  );
}
