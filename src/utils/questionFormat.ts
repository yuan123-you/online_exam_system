/**
 * 题目选项与答案解析规范化工具
 * 解决选项前缀重复（如 "A. A. xxx"）、前缀缺失、多题型判断逻辑与展示格式问题
 */

/**
 * 递归/循环去除选项开头的字母/序号前缀
 * 例如：
 * "A. 计算机" -> "计算机"
 * "A. A. 计算机" -> "计算机"
 * "(A) 计算机" -> "计算机"
 * "A、 计算机" -> "计算机"
 * "A: 计算机" -> "计算机"
 * "【A】计算机" -> "计算机"
 * "Apple" -> "Apple" (无标点修饰的不误伤)
 */
export function stripOptionPrefix(opt: unknown): string {
  if (opt == null) return ''
  let text = String(opt).trim()
  let prev = ''
  while (text !== prev) {
    prev = text
    text = text
      .replace(/^[\(\[（【][A-Za-z0-9][\)\]）】]\s*/, '')
      .replace(/^[A-Za-z0-9][.、:：)）]\s*/, '')
      .trim()
  }
  return text || String(opt).trim()
}

/**
 * 提取选项的字母键值（A/B/C/D...）
 * 若选项文本中包含明确的字母标签（如 "A. xxx"、"(B)"），优先提取该字母；
 * 否则根据选项在列表中的 index（0 -> A, 1 -> B）返回标准选项字母。
 */
export function extractOptionKey(opt: unknown, index?: number): string {
  if (opt != null) {
    const s = String(opt).trim()
    const m = s.match(/^[\(\[（【]?([A-Za-z0-9])[\)\]）】]?[.、:：)）\s]?/)
    if (m && /[A-Za-z]/.test(m[1])) {
      return m[1].toUpperCase()
    }
  }
  if (index !== undefined && index >= 0 && index < 26) {
    return String.fromCharCode(65 + index)
  }
  return ''
}

/**
 * 检查当前选项是否为题目的正确答案
 * 兼容单选、多选、判断题：
 * 1. 匹配选项字母（A、B、C、D）
 * 2. 匹配选项纯文本内容（去除前缀后比对）
 * 3. 匹配选项原始内容
 * 4. 判断题支持 "正确/对/true/T" 与 "错误/错/false/F" 的语义等价判定
 */
export function isOptionCorrect(
  q: { answer?: unknown[]; type?: string; options?: unknown[] },
  opt: unknown,
  index: number
): boolean {
  if (!q || !q.answer || !Array.isArray(q.answer) || q.answer.length === 0) {
    return false
  }

  const indexLetter = String.fromCharCode(65 + index)
  const optKey = extractOptionKey(opt, index)
  const cleanOpt = stripOptionPrefix(opt)
  const rawOpt = String(opt ?? '').trim()
  const qType = q.type || 'single'

  return q.answer.some((ans) => {
    if (ans == null) return false
    const ansStr = String(ans).trim()
    const ansClean = stripOptionPrefix(ansStr)
    const ansKey = extractOptionKey(ansStr)

    // 1. 字母完全匹配（如 answer 为 ["A"]，当前选项为 A）
    if (ansKey && (ansKey === indexLetter || ansKey === optKey)) {
      return true
    }

    // 2. 文本内容完全匹配（如 answer 为 ["微积分"]，选项文本为 "微积分" 或 "A. 微积分"）
    if (ansClean && (ansClean === cleanOpt || ansClean === rawOpt || ansStr === rawOpt)) {
      return true
    }

    // 3. 判断题语义等价判定
    if (qType === 'judge') {
      const isAnsTrue = /^(对|正确|true|t|√)$/i.test(ansClean) || ansKey === 'A'
      const isAnsFalse = /^(错|错误|false|f|×)$/i.test(ansClean) || ansKey === 'B'
      const isOptTrue = /^(对|正确|true|t|√)$/i.test(cleanOpt) || index === 0
      const isOptFalse = /^(错|错误|false|f|×)$/i.test(cleanOpt) || index === 1

      if (isAnsTrue && isOptTrue) return true
      if (isAnsFalse && isOptFalse) return true
    }

    return false
  })
}

/**
 * 格式化参考答案展示
 */
export function formatQuestionAnswer(q: {
  answer?: unknown[]
  type?: string
  options?: unknown[]
}): string {
  if (!q || !q.answer || !Array.isArray(q.answer) || q.answer.length === 0) {
    return ''
  }

  const qType = q.type || 'single'
  if (qType === 'fill' || qType === 'short' || qType === 'coding') {
    return q.answer.map((a) => String(a ?? '').trim()).filter(Boolean).join('，')
  }

  // 选择题/判断题：提取大写字母标签或标准化显示
  const letters: string[] = []
  for (const a of q.answer) {
    if (a == null) continue
    const str = String(a).trim()
    const key = extractOptionKey(str)
    if (key && /[A-Z]/.test(key)) {
      letters.push(key)
    } else if (qType === 'judge') {
      if (/^(对|正确|true|t|√)$/i.test(str)) letters.push('A')
      else if (/^(错|错误|false|f|×)$/i.test(str)) letters.push('B')
      else letters.push(str)
    } else {
      letters.push(str)
    }
  }

  return letters.length > 0 ? letters.join('，') : q.answer.join('，')
}
