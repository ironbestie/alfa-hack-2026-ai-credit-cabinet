"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { getApplication, getTimeline, statusStreamUrl } from "@/api/applications";
import { authHeader } from "@/api/http";
import { finalStatuses } from "@/lib/statuses";

const POLL_INTERVAL = 3000;
const STREAM_POLL_INTERVAL = 15000;

export function useApplicationStatus(id: string) {
  const queryClient = useQueryClient();
  const [streaming, setStreaming] = useState(false);

  const application = useQuery({
    queryKey: ["application", id],
    queryFn: () => getApplication(id),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (status && finalStatuses.includes(status)) return false;
      return streaming ? STREAM_POLL_INTERVAL : POLL_INTERVAL;
    },
  });

  const status = application.data?.status;
  const updatedAt = application.data?.updatedAt;

  const timeline = useQuery({
    queryKey: ["application", id, "timeline", status, updatedAt],
    queryFn: () => getTimeline(id),
    enabled: !!status,
    placeholderData: (previous) => previous,
  });

  const loaded = application.isSuccess;

  useEffect(() => {
    const url = statusStreamUrl(id);
    if (!url || !loaded) return;

    const controller = new AbortController();
    readStream(url, controller.signal, {
      onOpen: () => setStreaming(true),
      onEvent: () => queryClient.invalidateQueries({ queryKey: ["application", id] }),
    }).finally(() => setStreaming(false));

    return () => controller.abort();
  }, [id, loaded, queryClient]);

  return { application, timeline, streaming };
}

type StreamHandlers = {
  onOpen: () => void;
  onEvent: () => void;
};

async function readStream(url: string, signal: AbortSignal, { onOpen, onEvent }: StreamHandlers) {
  const headers = new Headers({ Accept: "text/event-stream" });
  const auth = authHeader();
  if (auth) headers.set("Authorization", auth);

  try {
    const response = await fetch(url, { headers, signal, cache: "no-store" });
    if (!response.ok || !response.body) return;
    onOpen();

    const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
    let buffer = "";
    while (true) {
      const { value, done } = await reader.read();
      if (done) return;
      buffer += value;
      const chunks = buffer.split(/\r?\n\r?\n/);
      buffer = chunks.pop() ?? "";
      if (chunks.some((chunk) => /^data:/m.test(chunk))) onEvent();
    }
  } catch {}
}
