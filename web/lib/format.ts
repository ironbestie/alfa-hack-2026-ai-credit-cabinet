const rub = new Intl.NumberFormat("ru-RU", {
  style: "currency",
  currency: "RUB",
  maximumFractionDigits: 0,
});

const percent = new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 1 });

const dateFormat = new Intl.DateTimeFormat("ru-RU");

const timeFormat = new Intl.DateTimeFormat("ru-RU", {
  hour: "2-digit",
  minute: "2-digit",
});

export function formatRub(value: number) {
  return rub.format(value);
}

export function formatRubShort(value: number) {
  if (value >= 1_000_000) return `${percent.format(value / 1_000_000)} млн ₽`;
  if (value >= 1_000) return `${percent.format(value / 1_000)} тыс. ₽`;
  return formatRub(value);
}

export function formatRate(value: number) {
  return `${percent.format(value)}%`;
}

export function formatDate(iso: string) {
  return dateFormat.format(new Date(iso));
}

export function formatTime(iso: string) {
  return timeFormat.format(new Date(iso));
}

export function pluralize(n: number, one: string, few: string, many: string) {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return one;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return few;
  return many;
}

export function formatMonths(n: number) {
  return `${n} ${pluralize(n, "месяц", "месяца", "месяцев")}`;
}

export function yearsSince(iso: string) {
  const from = new Date(iso);
  const now = new Date();
  let years = now.getFullYear() - from.getFullYear();
  const beforeAnniversary =
    now.getMonth() < from.getMonth() ||
    (now.getMonth() === from.getMonth() && now.getDate() < from.getDate());
  if (beforeAnniversary) years -= 1;
  return years;
}
