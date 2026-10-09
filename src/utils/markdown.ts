import katex from 'katex'

/**
 * 转义基础 HTML 字符，防止 XSS 攻击
 */
export function escapeHtml(s: unknown): string {
  if (s == null) return ''
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * 渲染 LaTeX 公式
 */
export function renderLatex(latex: string, displayMode: boolean): string {
  try {
    return katex.renderToString(latex.trim(), {
      displayMode,
      throwOnError: false,
      strict: false,
    })
  } catch {
    return escapeHtml(latex)
  }
}

/**
 * 针对内联文本（如选项内容、短题干标签）的富文本渲染
 * 仅渲染行内公式、行内代码、粗体/斜体，不包含块级元素（<p>、<pre>、<h1>-<h6>）
 */
export function renderInlineRichContent(text: unknown): string {
  if (text == null) return ''
  let content = String(text)
  if (!content.trim()) return ''

  const mathPlaceholders: string[] = []

  // 1. 提取并保护行内 LaTeX 公式: $...$ 或 \(...\)
  content = content.replace(/\$([^\$\n]+?)\$/g, (_, math) => {
    const idx = mathPlaceholders.length
    mathPlaceholders.push(renderLatex(math, false))
    return `%%INLINE_MATH_${idx}%%`
  })
  content = content.replace(/\\\(([\s\S]*?)\\\)/g, (_, math) => {
    const idx = mathPlaceholders.length
    mathPlaceholders.push(renderLatex(math, false))
    return `%%INLINE_MATH_${idx}%%`
  })

  // 2. 基础 HTML 转义
  content = escapeHtml(content)

  // 3. 行内代码
  content = content.replace(/`([^`]+)`/g, '<code class="md-inline">$1</code>')

  // 4. 加粗与斜体
  content = content.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  content = content.replace(/\*([^*]+)\*/g, '<em>$1</em>')

  // 5. 还原 LaTeX 公式
  content = content.replace(/%%INLINE_MATH_(\d+)%%/g, (_, idx) => {
    return mathPlaceholders[parseInt(idx, 10)] || ''
  })

  return content
}

/**
 * 针对长文本（题干、解析、AI对话消息）的完整 Markdown 与 LaTeX 富文本渲染
 * 支持代码块（保留换行与缩进）、块级公式、行内公式、段落、列表、标题等
 */
export function renderRichContent(text: unknown): string {
  if (text == null) return ''
  let content = String(text)
  if (!content.trim()) return ''

  const mathPlaceholders: string[] = []
  const codePlaceholders: string[] = []

  // 1. 提取并保护块级公式: $$...$$ 或 \[...\]
  content = content.replace(/\$\$([\s\S]*?)\$\$/g, (_, math) => {
    const idx = mathPlaceholders.length
    mathPlaceholders.push(renderLatex(math, true))
    return `\n%%BLOCK_MATH_${idx}%%\n`
  })
  content = content.replace(/\\\[([\s\S]*?)\\\]/g, (_, math) => {
    const idx = mathPlaceholders.length
    mathPlaceholders.push(renderLatex(math, true))
    return `\n%%BLOCK_MATH_${idx}%%\n`
  })

  // 2. 提取并保护行内公式: $...$ 或 \(...\)
  content = content.replace(/\$([^\$\n]+?)\$/g, (_, math) => {
    const idx = mathPlaceholders.length
    mathPlaceholders.push(renderLatex(math, false))
    return `%%INLINE_MATH_${idx}%%`
  })
  content = content.replace(/\\\(([\s\S]*?)\\\)/g, (_, math) => {
    const idx = mathPlaceholders.length
    mathPlaceholders.push(renderLatex(math, false))
    return `%%INLINE_MATH_${idx}%%`
  })

  // 3. 提取并保护多行代码块 ```lang ... ```
  content = content.replace(/```(\w*)\n?([\s\S]*?)```/g, (_, lang, code) => {
    const idx = codePlaceholders.length
    const cleanCode = escapeHtml(code.replace(/^\n+|\n+$/g, ''))
    const langClass = lang ? ` class="language-${escapeHtml(lang)}"` : ''
    codePlaceholders.push(`<pre class="md-code"><code${langClass}>${cleanCode}</code></pre>`)
    return `\n%%BLOCK_CODE_${idx}%%\n`
  })

  // 4. 转义常规文本
  content = escapeHtml(content)

  // 5. 行内代码
  content = content.replace(/`([^`]+)`/g, '<code class="md-inline">$1</code>')

  // 6. 标题 (####, ###, ##, #)
  content = content.replace(/^####\s+(.+)$/gm, '<h6 class="md-h6">$1</h6>')
  content = content.replace(/^###\s+(.+)$/gm, '<h5 class="md-h5">$1</h5>')
  content = content.replace(/^##\s+(.+)$/gm, '<h4 class="md-h4">$1</h4>')
  content = content.replace(/^#\s+(.+)$/gm, '<h3 class="md-h3">$1</h3>')

  // 7. 加粗与斜体
  content = content.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  content = content.replace(/\*([^*]+)\*/g, '<em>$1</em>')

  // 8. 列表项与分割线
  content = content.replace(/^---+$/gm, '<hr class="md-hr">')
  content = content.replace(/^(\d+)\.\s+(.+)$/gm, '<div class="md-li-num"><span class="md-num">$1.</span> $2</div>')
  content = content.replace(/^[-*]\s+(.+)$/gm, '<div class="md-li-bullet"><span class="md-bullet">•</span> $1</div>')

  // 9. 段落与换行处理
  content = content.replace(/\n{2,}/g, '<br><br>')
  content = content.replace(/\n/g, '<br>')

  // 10. 还原代码块与数学公式
  content = content.replace(/<br>\s*%%BLOCK_CODE_(\d+)%%\s*<br>/g, (_, idx) => {
    return codePlaceholders[parseInt(idx, 10)] || ''
  })
  content = content.replace(/%%BLOCK_CODE_(\d+)%%/g, (_, idx) => {
    return codePlaceholders[parseInt(idx, 10)] || ''
  })

  content = content.replace(/<br>\s*%%BLOCK_MATH_(\d+)%%\s*<br>/g, (_, idx) => {
    return `<div class="md-math-block">${mathPlaceholders[parseInt(idx, 10)] || ''}</div>`
  })
  content = content.replace(/%%BLOCK_MATH_(\d+)%%/g, (_, idx) => {
    return `<div class="md-math-block">${mathPlaceholders[parseInt(idx, 10)] || ''}</div>`
  })

  content = content.replace(/%%INLINE_MATH_(\d+)%%/g, (_, idx) => {
    return mathPlaceholders[parseInt(idx, 10)] || ''
  })

  return content
}
