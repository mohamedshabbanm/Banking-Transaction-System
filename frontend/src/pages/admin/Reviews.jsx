import { get } from '../../api';
import { ErrorCard, Proverb, Skeleton, useLoader } from '../../components/ui';
import ReviewTable from './ReviewTable';

export default function Reviews() {
  const { data, error, loading, reload } = useLoader(() => get('/api/admin/reviews'));
  return (
    <>
      <div>
        <h1>Review queue</h1>
        <Proverb style={{ textAlign: 'left', color: 'var(--muted)' }}>الباب اللي يجيلك منه الريح، سدّه واستريح</Proverb>
        <p className="small muted" style={{ marginTop: 4 }}>High-risk transactions wait here with the customer's money held. Approve sends it; reject gives it back.</p>
      </div>
      {loading && <Skeleton height={200} />}
      {error && <ErrorCard error={error} onRetry={reload} />}
      {data && <section className="card"><ReviewTable items={data} onChange={reload} /></section>}
    </>
  );
}
