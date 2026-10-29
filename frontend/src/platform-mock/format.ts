// Display formats shared with the backend DisplayFormat (spec 2.4); percent-like inputs are percent numbers.

const amountFormat = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 })
const wanFormat = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 })

function fixed(value: number, digits: number): string {
  const text = value.toFixed(digits)
  // Avoid "-0.00" for tiny negatives.
  return Number(text) === 0 ? (0).toFixed(digits) : text
}

export function formatAmount(yuan: number): string {
  return `${amountFormat.format(yuan)} 元`
}

/** Card display: yuan rounded to the nearest 萬. */
export function formatWan(yuan: number): string {
  return `${wanFormat.format(Math.round(yuan / 10_000))} 萬`
}

export function formatPercent(value: number | string): string {
  return `${fixed(Number(value), 2)}%`
}

export function formatPoints(diff: number): string {
  // The sign follows the rounded value, so 0.001 reads 0.00 without a plus.
  const text = fixed(diff, 2)
  return `${Number(text) > 0 ? '+' : ''}${text} 個百分點`
}
