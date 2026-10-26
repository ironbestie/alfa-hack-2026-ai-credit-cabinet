import { Suspense } from "react";
import { ApplicationTracker, TrackerSkeleton } from "@/components/tracker/application-tracker";

export default function ApplicationPage() {
  return (
    <div className="mx-auto max-w-4xl px-4 py-10 sm:px-6">
      <Suspense fallback={<TrackerSkeleton />}>
        <ApplicationTracker />
      </Suspense>
    </div>
  );
}
