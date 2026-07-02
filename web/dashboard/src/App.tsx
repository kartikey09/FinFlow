import { SpendByTeamChart } from './components/SpendByTeamChart';
import { CommitmentTable } from './components/CommitmentTable';
import { TriggerIngestButton } from './components/TriggerIngestButton';

/**
 * The whole dashboard. One page, three regions — spend chart, commitment
 * table, admin button. The plan is explicit: "One page is fine; don't over-
 * design the UI." Kept small and honest.
 */
export default function App() {
  return (
    <div className="max-w-6xl mx-auto p-6 space-y-6">
      <header className="flex items-baseline justify-between">
        <h1 className="text-2xl font-semibold tracking-tight">FinFlow</h1>
        <TriggerIngestButton />
      </header>

      <section className="bg-white rounded-xl shadow-sm border border-slate-200 p-5">
        <h2 className="text-sm font-medium text-slate-600 mb-3">
          Spend by team · last 30 days
        </h2>
        <SpendByTeamChart />
      </section>

      <section className="bg-white rounded-xl shadow-sm border border-slate-200 p-5">
        <h2 className="text-sm font-medium text-slate-600 mb-3">
          Commitment utilization
        </h2>
        <CommitmentTable />
      </section>
    </div>
  );
}
