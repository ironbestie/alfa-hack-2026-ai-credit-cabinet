import { RoleGuard } from "@/components/role-guard";

export default function ApplicationsLayout({ children }: { children: React.ReactNode }) {
  return <RoleGuard role="client">{children}</RoleGuard>;
}
