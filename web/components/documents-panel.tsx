"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, FileText, Loader2, Upload } from "lucide-react";
import { useRef } from "react";
import { toast } from "sonner";
import { downloadDocument, listDocuments, uploadDocument } from "@/api/applications";
import type { ApplicationDocument } from "@/api/types";
import { ErrorState } from "@/components/error-state";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDate } from "@/lib/format";

const MAX_SIZE = 10 * 1024 * 1024;
const ACCEPT = ".pdf,.jpg,.jpeg,.png";

function formatSize(bytes: number | null) {
  if (!bytes) return "";
  return bytes > 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} МБ` : `${Math.ceil(bytes / 1024)} КБ`;
}

export function DocumentsPanel({ applicationId, canUpload }: { applicationId: string; canUpload: boolean }) {
  const queryClient = useQueryClient();
  const input = useRef<HTMLInputElement>(null);

  const documents = useQuery({
    queryKey: ["application", applicationId, "documents"],
    queryFn: () => listDocuments(applicationId),
  });

  const upload = useMutation({
    mutationFn: (file: File) => uploadDocument(applicationId, file),
    onSuccess: () => {
      toast.success("Документ загружен, заявка вернулась к специалисту");
      queryClient.invalidateQueries({ queryKey: ["application", applicationId] });
    },
    onError: (error) => toast.error(error.message),
  });

  const download = useMutation({
    mutationFn: async (doc: ApplicationDocument) => {
      const blob = await downloadDocument(applicationId, doc.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = doc.originalFileName;
      link.click();
      URL.revokeObjectURL(url);
    },
    onError: (error) => toast.error(error.message),
  });

  function onFile(file: File | undefined) {
    if (!file) return;
    if (!/\.(pdf|jpe?g|png)$/i.test(file.name)) {
      toast.error("Подойдёт PDF, JPG или PNG");
      return;
    }
    if (file.size > MAX_SIZE) {
      toast.error("Файл больше 10 МБ");
      return;
    }
    upload.mutate(file);
  }

  if (!canUpload && documents.data?.length === 0) return null;

  return (
    <section className="space-y-3 rounded-2xl border border-border bg-card p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold">Документы</h2>
          {canUpload && (
            <p className="text-sm text-muted-foreground">Загрузите документ, который запросил специалист. PDF, JPG или PNG до 10 МБ.</p>
          )}
        </div>
        {canUpload && (
          <>
            <input
              ref={input}
              type="file"
              accept={ACCEPT}
              className="hidden"
              onChange={(e) => {
                onFile(e.target.files?.[0]);
                e.target.value = "";
              }}
            />
            <Button className="h-10 rounded-xl" disabled={upload.isPending} onClick={() => input.current?.click()}>
              {upload.isPending ? <Loader2 className="animate-spin" /> : <Upload />}
              Загрузить
            </Button>
          </>
        )}
      </div>

      {documents.isPending ? (
        <Skeleton className="h-12 w-full" />
      ) : documents.isError ? (
        <ErrorState error={documents.error} onRetry={() => documents.refetch()} />
      ) : documents.data.length === 0 ? (
        <p className="text-sm text-muted-foreground">Пока ничего не загружено</p>
      ) : (
        <ul className="divide-y divide-border">
          {documents.data.map((doc) => (
            <li key={doc.id} className="flex items-center gap-3 py-2 text-sm">
              <FileText className="size-4 shrink-0 text-muted-foreground" />
              <span className="flex-1 truncate">{doc.originalFileName}</span>
              <span className="text-muted-foreground">
                {formatSize(doc.sizeBytes)} · {formatDate(doc.uploadDate)}
              </span>
              <Button
                size="icon-sm"
                variant="ghost"
                aria-label={`Скачать ${doc.originalFileName}`}
                onClick={() => download.mutate(doc)}
              >
                <Download />
              </Button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
