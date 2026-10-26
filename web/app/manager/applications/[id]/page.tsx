import { Suspense } from "react";
import { ManagerApplicationCard } from "@/components/manager/application-card";
import { TrackerSkeleton } from "@/components/tracker/application-tracker";

export default function ManagerApplicationPage() {
  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6">
      <Suspense fallback={<TrackerSkeleton />}>
        <ManagerApplicationCard />
      </Suspense>
    </div>
  );
}
