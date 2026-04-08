/**
 * 时序降采样步长单位选项。
 */
export const TIME_PRECISION_UNIT_OPTIONS = [
  { value: 'ms', label: '毫秒', factor: 1 },
  { value: 's', label: '秒', factor: 1000 },
  { value: 'min', label: '分', factor: 60 * 1000 },
  { value: 'h', label: '小时', factor: 60 * 60 * 1000 },
  { value: 'd', label: '天', factor: 24 * 60 * 60 * 1000 }
]

const getTimePrecisionUnit = (value) =>
  TIME_PRECISION_UNIT_OPTIONS.find(item => item.value === value) || TIME_PRECISION_UNIT_OPTIONS[0]

const formatPrecisionNumber = (value) => {
  if (!Number.isFinite(value)) {
    return ''
  }
  if (Math.abs(value) >= 100 || Number.isInteger(value)) {
    return String(Math.round(value * 100) / 100).replace(/\.0+$/, '')
  }
  return value.toFixed(2).replace(/\.?0+$/, '')
}

/**
 * 将“数值 + 单位”转换为毫秒。
 */
export const parsePrecisionValueToMs = (value, unit) => {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue) || numericValue <= 0) {
    return null
  }
  const resolvedUnit = getTimePrecisionUnit(unit)
  return Math.max(1, Math.round(numericValue * resolvedUnit.factor))
}

/**
 * 将毫秒格式化为更适合用户理解的时间文本。
 */
export const formatPrecisionMs = (value) => {
  const milliseconds = Number(value)
  if (!Number.isFinite(milliseconds) || milliseconds <= 0) {
    return ''
  }

  for (let index = TIME_PRECISION_UNIT_OPTIONS.length - 1; index >= 0; index -= 1) {
    const unit = TIME_PRECISION_UNIT_OPTIONS[index]
    if (milliseconds >= unit.factor) {
      return `${formatPrecisionNumber(milliseconds / unit.factor)} ${unit.label}`
    }
  }

  return `${formatPrecisionNumber(milliseconds)} 毫秒`
}
