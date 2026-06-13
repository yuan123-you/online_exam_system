/**
 * 账号密码校验工具
 *
 * 规则定义（2025-09 修订）：
 *
 * ┌────────────┬──────────────────────────────────────────────┐
 * │  字段       │  规则                                         │
 * ├────────────┼──────────────────────────────────────────────┤
 * │  账号       │  4-20 位，字母 + 数字，允许纯数字               │
 * │            │  不区分大小写（校验时自动转小写）                │
 * │            │  禁止空格、下划线及任何特殊符号                  │
 * ├────────────┼──────────────────────────────────────────────┤
 * │  密码       │  6-18 位，仅允许字母 + 数字                    │
 * │            │  区分大小写、不强制特殊符号                     │
 * │            │  无复杂度分级、无黑名单、无键盘序检测            │
 * │            │  允许纯数字密码（适配低年级学生）                │
 * └────────────┴──────────────────────────────────────────────┘
 */

/** 校验结果 */
export interface ValidationResult {
  valid: boolean
  /** 空字符串表示无错误 */
  message: string
}

// ===================== 账号校验 =====================

/** 账号规则：4-20 位字母数字，允许纯数字，不含特殊符号 */
const USERNAME_REGEX = /^[a-zA-Z0-9]{4,20}$/

/**
 * 校验账号格式
 *
 * @param username 原始输入（前后空白会在校验前自动 trim）
 * @returns { valid, message }
 *
 * @example
 * validateUsername('abc123')    // { valid: true, message: '' }
 * validateUsername('123456')    // { valid: true, message: '' }
 * validateUsername('abc')       // { valid: false, message: '账号仅支持…' }
 * validateUsername('user name') // { valid: false, message: '账号仅支持…' }
 * validateUsername('abc_123')   // { valid: false, message: '账号仅支持…' }
 */
export function validateUsername(username: unknown): ValidationResult {
  if (username == null || (typeof username !== 'string' && typeof username !== 'number')) {
    return { valid: false, message: '账号不能为空' }
  }

  const str = String(username).trim()

  if (!str) {
    return { valid: false, message: '账号不能为空' }
  }

  if (!USERNAME_REGEX.test(str)) {
    return { valid: false, message: '账号仅支持 4-20 位字母和数字，不能包含空格、下划线及任何特殊符号' }
  }

  return { valid: true, message: '' }
}

/**
 * 标准化账号（转小写，去除前后空白）
 * 用于存入数据库或登录比较前调用
 */
export function normalizeUsername(username: string): string {
  return username.trim().toLowerCase()
}

// ===================== 密码校验 =====================

/** 密码规则：6-18 位，仅允许字母数字 */
const PASSWORD_REGEX = /^[a-zA-Z0-9]{6,18}$/

/**
 * 校验密码格式
 *
 * @param password 原始输入
 * @returns { valid, message }
 *
 * @example
 * validatePassword('abc123')   // { valid: true, message: '' }
 * validatePassword('123456')   // { valid: true, message: '' }
 * validatePassword('abcdef')   // { valid: true, message: '' }
 * validatePassword('abc')      // { valid: false, message: '密码仅支持…' }
 * validatePassword('pass word') // { valid: false, message: '密码仅支持…' }
 * validatePassword('pass@123')  // { valid: false, message: '密码仅支持…' }
 */
export function validatePassword(password: unknown): ValidationResult {
  if (password == null || (typeof password !== 'string' && typeof password !== 'number')) {
    return { valid: false, message: '密码不能为空' }
  }

  const str = String(password)

  if (!str) {
    return { valid: false, message: '密码不能为空' }
  }

  if (!PASSWORD_REGEX.test(str)) {
    return { valid: false, message: '密码仅支持 6-18 位字母和数字，不能包含特殊符号' }
  }

  return { valid: true, message: '' }
}

// ===================== 同时校验 =====================

/**
 * 同时校验账号和密码
 * 适用于表单提交时一次校验两个字段
 */
export function validateLoginForm(username: unknown, password: unknown): {
  usernameResult: ValidationResult
  passwordResult: ValidationResult
  /** 是否全部通过 */
  valid: boolean
} {
  const usernameResult = validateUsername(username)
  const passwordResult = validatePassword(password)
  return {
    usernameResult,
    passwordResult,
    valid: usernameResult.valid && passwordResult.valid,
  }
}
