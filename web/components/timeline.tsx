import type { ApplicationEvent } from "@/api/types";
import { formatDate, formatTime } from "@/lib/format";
import { eventActorLabels } from "@/lib/statuses";

export function Timeline({ events }: { events: ApplicationEvent[] }) {
  if (events.length === 0) {
    return <p className="text-sm text-muted-foreground">Событий пока нет</p>;
  }

  return (
    <ul className="space-y-2">
      {[...events].reverse().map((event, i) => (
        <li
          key={`${event.timestamp}-${event.eventType}-${i}`}
          className="flex flex-wrap gap-x-4 gap-y-1 rounded-xl border border-border bg-card px-4 py-3 text-sm"
        >
          <span className="w-24 shrink-0 text-muted-foreground" title={formatDate(event.timestamp)}>
            {formatTime(event.timestamp)}
          </span>
          <span className="min-w-48 flex-1">{event.description}</span>
          <span className="text-muted-foreground">{eventActorLabels[event.actor.toLowerCase()] ?? event.actor}</span>
        </li>
      ))}
    </ul>
  );
}
