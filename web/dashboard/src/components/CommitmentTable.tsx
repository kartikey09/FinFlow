import { useQuery } from '@tanstack/react-query';

type CommitmentHealth = {
  commitmentId: string;
  vendor: string | null;
  accountId: string | null;
  status: 'HEALTHY' | 'UNDERUTILIZED' | 'SATURATED' | 'EXPIRING_SOON';
  lastAlertType: string | null;
  lastAlertAt: string | null;
  latestFraction: number | null;
  latestUsedUsd: number | null;
  latestAvailableUsd: number | null;
};

async function fetchCommitments(): Promise<CommitmentHealth[]> {
  const res = await fetch('/api/v1/commitments');
  if (!res.ok) throw new Error(`commitments: HTTP ${res.status}`);
  return res.json();
}

function statusPill(status: CommitmentHealth['status']) {
  const map = {
    HEALTHY:        'bg-emerald-100 text-emerald-800',
    UNDERUTILIZED:  'bg-amber-100 text-amber-800',
    SATURATED:      'bg-red-100 text-red-800',
    EXPIRING_SOON:  'bg-slate-200 text-slate-800',
  } as const;
  return (
    <span className={`inline-block px-2 py-0.5 text-xs font-medium rounded ${map[status]}`}>
      {status.replace('_', ' ')}
    </span>
  );
}

function fmtPct(f: number | null) {
  if (f == null) return '—';
  return `${(f * 100).toFixed(0)}%`;
}

export function CommitmentTable() {
  const q = useQuery({ queryKey: ['commitments'], queryFn: fetchCommitments });

  if (q.isLoading) return <div className="text-sm text-slate-500 py-6">Loading…</div>;
  if (q.isError)   return <div className="text-sm text-red-600 py-6">Failed to load commitments.</div>;
  if (!q.data || q.data.length === 0) {
    return <div className="text-sm text-slate-500 py-6">No commitments known yet.</div>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full text-sm">
        <thead className="text-slate-500 text-xs uppercase tracking-wide">
          <tr>
            <th className="text-left py-2 pr-4">Commitment</th>
            <th className="text-left py-2 pr-4">Vendor</th>
            <th className="text-left py-2 pr-4">Utilization</th>
            <th className="text-left py-2 pr-4">Status</th>
            <th className="text-left py-2 pr-4">Last alert</th>
          </tr>
        </thead>
        <tbody>
          {q.data.map((c) => (
            <tr key={c.commitmentId} className="border-t border-slate-100">
              <td className="py-2 pr-4 font-mono text-xs truncate max-w-xs" title={c.commitmentId}>
                {c.commitmentId}
              </td>
              <td className="py-2 pr-4">{c.vendor ?? '—'}</td>
              <td className="py-2 pr-4">{fmtPct(c.latestFraction)}</td>
              <td className="py-2 pr-4">{statusPill(c.status)}</td>
              <td className="py-2 pr-4 text-xs text-slate-500">
                {c.lastAlertType ?? '—'}
                {c.lastAlertAt ? ` · ${new Date(c.lastAlertAt).toLocaleString()}` : ''}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
