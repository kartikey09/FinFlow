import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

async function triggerIngest(vendor: 'aws' | 'gcp') {
  const res = await fetch(`/api/v1/admin/trigger-ingest?vendor=${vendor}`, { method: 'POST' });
  if (!res.ok) throw new Error(`trigger: HTTP ${res.status}`);
  return res.json();
}

/**
 * "Trigger billing pull" button. Optimistically pokes the ingestor and
 * invalidates the queries so the chart/table re-fetch when data arrives.
 * The real work happens in the ingestor's scheduled poll — this is just the
 * demo affordance.
 */
export function TriggerIngestButton() {
  const qc = useQueryClient();
  const [vendor, setVendor] = useState<'aws' | 'gcp'>('aws');
  const m = useMutation({
    mutationFn: () => triggerIngest(vendor),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['spend-by-team'] });
      qc.invalidateQueries({ queryKey: ['commitments'] });
    },
  });

  return (
    <div className="flex items-center gap-2">
      <select
        value={vendor}
        onChange={(e) => setVendor(e.target.value as 'aws' | 'gcp')}
        className="text-sm border border-slate-300 rounded px-2 py-1 bg-white"
      >
        <option value="aws">AWS</option>
        <option value="gcp">GCP</option>
      </select>
      <button
        onClick={() => m.mutate()}
        disabled={m.isPending}
        className="text-sm font-medium bg-slate-900 text-white rounded px-3 py-1 hover:bg-slate-800 disabled:opacity-50"
      >
        {m.isPending ? 'Triggering…' : 'Trigger pull'}
      </button>
    </div>
  );
}
