import { describe, it, expect } from "vitest";
import { renderRichContent, renderInlineRichContent, escapeHtml } from "./markdown";

describe("markdown utils", () => {
  describe("escapeHtml", () => {
    it("should escape special characters", () => {
      expect(escapeHtml("<script>alert('xss')</script>")).toBe("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;");
      expect(escapeHtml(null)).toBe("");
      expect(escapeHtml(undefined)).toBe("");
    });
  });

  describe("renderInlineRichContent", () => {
    it("should render inline LaTeX formula", () => {
      const html = renderInlineRichContent("函数 $f(x)=x^2$ 的导数");
      expect(html).toContain('class="katex"');
      expect(html).toContain("函数");
      expect(html).toContain("的导数");
    });

    it("should render inline code and bold", () => {
      const html = renderInlineRichContent("使用 `const` 和 **let** 声明");
      expect(html).toContain('<code class="md-inline">const</code>');
      expect(html).toContain('<strong>let</strong>');
    });

    it("should not contain block-level wrappers", () => {
      const html = renderInlineRichContent("普通文本 $x+1$");
      expect(html).not.toContain("<p>");
      expect(html).not.toContain("<pre>");
    });
  });

  describe("renderRichContent", () => {
    it("should render display math block", () => {
      const html = renderRichContent("计算极限：\n$$\\lim_{x \\to 0} \\frac{\\sin x}{x} = 1$$");
      expect(html).toContain('class="md-math-block"');
      expect(html).toContain('class="katex-display"');
    });

    it("should render code blocks with preserved indentation and without unwanted breaks inside", () => {
      const code = "```python\ndef add(a, b):\n    return a + b\n```";
      const html = renderRichContent(code);
      expect(html).toContain('<pre class="md-code"><code class="language-python">');
      expect(html).toContain("def add(a, b):");
      expect(html).toContain("    return a + b");
    });

    it("should render markdown headers, bold, and line breaks", () => {
      const text = "### 题目解析\n**注意**：这是关键步骤。\n第二行说明。";
      const html = renderRichContent(text);
      expect(html).toContain('<h5 class="md-h5">题目解析</h5>');
      expect(html).toContain('<strong>注意</strong>');
      expect(html).toContain("<br>");
    });
  });
});
