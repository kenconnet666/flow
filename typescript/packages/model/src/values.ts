/**
 * 条件里两个值怎样比：Java 的 `Values` 与这里是同一套规则，两个后端与前端对同一个条件得到同一个结果（spec/conditions 的样例两边都跑）。
 *
 * - 数字、大整数、能读成数字的文字（`"19.90"`、`"1e3"`）按**精确的小数**比较，不经浮点数，长小数、大整数不丢精度；
 * - 其余写成文字后按**编码顺序**比较（不按语言环境，ISO 日期按文字比就是先后）；
 * - 等于：数字与数字文字视为相等（表单里的 `"5"` 与变量里的 `5`），`true` 与 `"true"` 也相等。
 *
 * 与组件库 `matchesCondition` 的不同只在两处：那边数字转成浮点数比、文字用 `localeCompare`；这里两个后端要逐字节一致，所以用精确小数与编码顺序。
 */

/** 拆开的小数：符号、去掉前导零的整数位、去掉末尾零的小数位 */
interface DecimalParts {
  readonly negative: boolean;
  readonly int: string;
  readonly frac: string;
}

const NUMERIC = /^([+-]?)(\d+)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$|^([+-]?)\.(\d+)(?:[eE]([+-]?\d+))?$/;

/** 能读成数字的文字、数字、大整数 → 拆开的小数；读不成时是 undefined */
function decimalOf(value: unknown): DecimalParts | undefined {
  if (typeof value === 'bigint') return parse(value.toString());
  if (typeof value === 'number') return Number.isFinite(value) ? parse(String(value)) : undefined;
  if (typeof value === 'string') return parse(value.trim());
  return undefined;
}

function parse(text: string): DecimalParts | undefined {
  const match = NUMERIC.exec(text);
  if (!match) return undefined;
  const [, sign1, int1, frac1, exp1, sign2, frac2, exp2] = match;
  const sign = sign1 ?? sign2 ?? '';
  const intDigits = int1 ?? '';
  const fracDigits = frac1 ?? frac2 ?? '';
  const exponent = Number(exp1 ?? exp2 ?? 0);
  // 小数点按指数移动后，再拆成整数位与小数位
  const digits = intDigits + fracDigits;
  const point = intDigits.length + exponent;
  const padded = point < 0 ? '0'.repeat(-point) + digits : point > digits.length ? digits + '0'.repeat(point - digits.length) : digits;
  const at = Math.max(point, 0);
  const int = padded.slice(0, at).replace(/^0+/, '');
  const frac = padded.slice(at).replace(/0+$/, '');
  const zero = int === '' && frac === '';
  return { negative: sign === '-' && !zero, int, frac };
}

/** 两个非负小数比大小 */
function compareMagnitude(a: DecimalParts, b: DecimalParts): number {
  if (a.int.length !== b.int.length) return a.int.length < b.int.length ? -1 : 1;
  if (a.int !== b.int) return a.int < b.int ? -1 : 1;
  const width = Math.max(a.frac.length, b.frac.length);
  const x = a.frac.padEnd(width, '0');
  const y = b.frac.padEnd(width, '0');
  return x === y ? 0 : x < y ? -1 : 1;
}

function compareDecimals(a: DecimalParts, b: DecimalParts): number {
  if (a.negative !== b.negative) return a.negative ? -1 : 1;
  const magnitude = compareMagnitude(a, b);
  return a.negative ? -magnitude : magnitude;
}

/** 拆开的小数 → 整数尾数与小数位数（123.45 → 12345n, 2） */
function scaled(parts: DecimalParts): { mantissa: bigint; scale: number } {
  const digits = (parts.int + parts.frac).replace(/^0+/, '') || '0';
  const mantissa = BigInt(digits);
  return { mantissa: parts.negative ? -mantissa : mantissa, scale: parts.frac.length };
}

/** 两个值精确相加，写成小数文字（不带多余的零）；读不成数字的当作 0 */
export function decimalText(a: unknown, b: unknown): string {
  const zero: DecimalParts = { negative: false, int: '', frac: '' };
  const x = scaled(decimalOf(a) ?? zero);
  const y = scaled(decimalOf(b) ?? zero);
  const scale = Math.max(x.scale, y.scale);
  const sum = x.mantissa * 10n ** BigInt(scale - x.scale) + y.mantissa * 10n ** BigInt(scale - y.scale);
  const negative = sum < 0n;
  const digits = (negative ? -sum : sum).toString().padStart(scale + 1, '0');
  const int = digits.slice(0, digits.length - scale);
  const frac = digits.slice(digits.length - scale).replace(/0+$/, '');
  const text = frac ? `${int}.${frac}` : int;
  return negative && text !== '0' ? `-${text}` : text;
}

/** 值写成文字：null、undefined 是空字符串，对象写成 JSON */
export function toText(value: unknown): string {
  if (value == null) return '';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value as string | number | boolean | bigint);
}

/** 两个值比大小：负数、0、正数 */
export function compareValues(a: unknown, b: unknown): number {
  const x = decimalOf(a);
  const y = decimalOf(b);
  if (x && y) return compareDecimals(x, y);
  const left = toText(a);
  const right = toText(b);
  return left === right ? 0 : left < right ? -1 : 1;
}

/** 等于：两个都不是空、不是对象时按 compareValues 比 */
export function sameValue(a: unknown, b: unknown): boolean {
  return a === b || (a != null && b != null && typeof a !== 'object' && typeof b !== 'object' && compareValues(a, b) === 0);
}

/** 没有值：null、undefined、空字符串、空数组 */
export function isBlank(value: unknown): boolean {
  return value == null || value === '' || (Array.isArray(value) && value.length === 0);
}
