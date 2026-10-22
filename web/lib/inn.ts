const WEIGHTS_10 = [2, 4, 10, 3, 5, 9, 4, 6, 8];
const WEIGHTS_11 = [7, 2, 4, 10, 3, 5, 9, 4, 6, 8];
const WEIGHTS_12 = [3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8];

function checkDigit(digits: number[], weights: number[]) {
  const sum = weights.reduce((acc, w, i) => acc + w * digits[i], 0);
  return (sum % 11) % 10;
}

export function isInnFormat(value: string) {
  return /^(\d{10}|\d{12})$/.test(value);
}

export function isValidInn(value: string) {
  if (!isInnFormat(value)) return false;
  const digits = [...value].map(Number);
  if (digits.length === 10) {
    return checkDigit(digits, WEIGHTS_10) === digits[9];
  }
  return (
    checkDigit(digits, WEIGHTS_11) === digits[10] &&
    checkDigit(digits, WEIGHTS_12) === digits[11]
  );
}
