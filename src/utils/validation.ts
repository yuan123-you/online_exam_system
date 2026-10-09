/** Credential validation shared by login and student registration. */

/** 校验结果 */
export interface ValidationResult {
  valid: boolean
  /** 空字符串表示无错误 */
  message: string
}

// ===================== 账号校验 =====================

/** 账号规则：4-20 位字母数字，允许纯数字，不含特殊符号 */
const USERNAME_REGEX = /^[A-Za-z0-9_][A-Za-z0-9_.-]{3,31}$/

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
    return { valid: false, message: '账号需为4至32位字母、数字或下划线，可包含点和短横线' }
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
export function validatePassword(password: unknown, minimumLength = 6): ValidationResult {
  if (password == null || (typeof password !== 'string' && typeof password !== 'number')) {
    return { valid: false, message: '密码不能为空' }
  }

  const str = String(password)

  if (!str) {
    return { valid: false, message: '密码不能为空' }
  }

  if (str.length < minimumLength || str.length > 64 || new TextEncoder().encode(str).length > 72) {
    return { valid: false, message: `密码需为${minimumLength}至64个字符，且不能超过72个UTF-8字节` }
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
