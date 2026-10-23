import clsx from 'clsx';
import { Loader2 } from 'lucide-react';

export function Spinner({ className }: { className?: string }) {
  return <Loader2 aria-hidden className={clsx('animate-spin', className ?? 'h-4 w-4')} />;
}

export function FullPageSpinner() {
  return (
    <div className="flex h-full items-center justify-center" role="status" aria-label="Loading">
      <Spinner className="h-6 w-6 text-indigo-600" />
    </div>
  );
}
