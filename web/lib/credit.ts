export const MAX_PAYMENT_SHARE = 0.5;

export function monthlyPayment(amount: number, annualRate: number, termMonths: number) {
  if (termMonths <= 0) return 0;
  const r = annualRate / 12 / 100;
  if (r === 0) return amount / termMonths;
  return (amount * r) / (1 - Math.pow(1 + r, -termMonths));
}

export function calculateLoan(amount: number, annualRate: number, termMonths: number) {
  const payment = Math.round(monthlyPayment(amount, annualRate, termMonths));
  const total = payment * termMonths;
  return { payment, total, overpayment: total - amount };
}
