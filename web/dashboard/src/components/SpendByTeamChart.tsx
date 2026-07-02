import { useQuery } from '@tanstack/react-query';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';

type SpendByTeam = { team: string; costUsd: number };

async function fetchSpend(): Promise<SpendByTeam[]> {
  const res = await fetch('/api/v1/spend/by-team?period=last-30d');
  if (!res.ok) throw new Error(`spend: HTTP ${res.status}`);
  return res.json();
}

/**
 * Bar chart of spend-by-team. Backed by /api/v1/spend/by-team which the
 * projection maintains event-by-event, so this shows live-ish numbers.
 */
export function SpendByTeamChart() {
  const q = useQuery({ queryKey: ['spend-by-team'], queryFn: fetchSpend });

  if (q.isLoading) return <div className="text-sm text-slate-500 py-10">Loading…</div>;
  if (q.isError)   return <div className="text-sm text-red-600 py-10">Failed to load spend data.</div>;
  if (!q.data || q.data.length === 0) {
    return (
      <div className="text-sm text-slate-500 py-10">
        No spend yet — the ingestors' first poll cycle may still be in progress.
      </div>
    );
  }

  return (
    <div className="h-64">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={q.data}>
          <XAxis dataKey="team" tick={{ fontSize: 12 }} />
          <YAxis tick={{ fontSize: 12 }} tickFormatter={(v) => `$${v.toFixed(0)}`} />
          <Tooltip formatter={(v: number) => `$${v.toFixed(2)}`} />
          <Bar dataKey="costUsd" fill="#3b82f6" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
