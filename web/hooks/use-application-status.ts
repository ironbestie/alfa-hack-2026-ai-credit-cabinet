"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { getApplication, getTimeline, statusStreamUrl } from "@/api/applications";
import { finalStatuses } from "@/lib/statuses";

const POLL_INTERVAL = 3000;

export function useApplicationStatus(id: string) {
  const queryClient = useQueryClient();
  const [streaming, setStreaming] = useState(false);

  const application = useQuery({
    queryKey: ["application", id],
    queryFn: () => getApplication(id),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (streaming || (status && finalStatuses.includes(status))) return false;
      return POLL_INTERVAL;
    },
  });

  const status = application.data?.status;
  const finished = status !== undefined && finalStatuses.includes(status);

  const timeline = useQuery({
    queryKey: ["application", id, "timeline"],
    queryFn: () => getTimeline(id),
    refetchInterval: streaming || finished ? false : POLL_INTERVAL,
  });

  useEffect(() => {
    const url = statusStreamUrl(id);
    if (!url || typeof EventSource === "undefined") return;

    const source = new EventSource(url);
    source.onopen = () => setStreaming(true);
    source.addEventListener("status", () => {
      queryClient.invalidateQueries({ queryKey: ["application", id] });
    });
    source.onerror = () => {
      source.close();
      setStreaming(false);
    };

    return () => source.close();
  }, [id, queryClient]);

  return { application, timeline, streaming };
}
