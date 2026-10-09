import { describe, it, expect } from "vitest";
import {
  stripOptionPrefix,
  extractOptionKey,
  isOptionCorrect,
  formatQuestionAnswer,
} from "./questionFormat";

describe("questionFormat utils", () => {
  describe("stripOptionPrefix", () => {
    it("should strip simple letter prefixes", () => {
      expect(stripOptionPrefix("A. 微积分")).toBe("微积分");
      expect(stripOptionPrefix("B、 线性代数")).toBe("线性代数");
      expect(stripOptionPrefix("(C) 概率论")).toBe("概率论");
      expect(stripOptionPrefix("D: 离散数学")).toBe("离散数学");
      expect(stripOptionPrefix("【A】微积分")).toBe("微积分");
    });

    it("should strip repeated duplicate prefixes (e.g. A. A. xxx)", () => {
      expect(stripOptionPrefix("A. A. 微积分")).toBe("微积分");
      expect(stripOptionPrefix("A. (A) 微积分")).toBe("微积分");
      expect(stripOptionPrefix("A、 A. 微积分")).toBe("微积分");
      expect(stripOptionPrefix("(B) B. 线性代数")).toBe("线性代数");
    });

    it("should not strip actual text words starting with letters", () => {
      expect(stripOptionPrefix("Apple")).toBe("Apple");
      expect(stripOptionPrefix("Action is required")).toBe("Action is required");
      expect(stripOptionPrefix("正确")).toBe("正确");
      expect(stripOptionPrefix("错误")).toBe("错误");
    });

    it("should handle null and undefined safely", () => {
      expect(stripOptionPrefix(null)).toBe("");
      expect(stripOptionPrefix(undefined)).toBe("");
      expect(stripOptionPrefix("")).toBe("");
    });
  });

  describe("extractOptionKey", () => {
    it("should extract key letter from prefixed options", () => {
      expect(extractOptionKey("A. 选项内容", 0)).toBe("A");
      expect(extractOptionKey("b. 选项内容", 1)).toBe("B");
      expect(extractOptionKey("(C) 选项内容", 2)).toBe("C");
    });

    it("should fall back to index letter when option has no letter prefix", () => {
      expect(extractOptionKey("选项内容", 0)).toBe("A");
      expect(extractOptionKey("选项内容", 1)).toBe("B");
      expect(extractOptionKey("选项内容", 2)).toBe("C");
      expect(extractOptionKey("选项内容", 3)).toBe("D");
    });
  });

  describe("isOptionCorrect", () => {
    it("should match single choice by letter", () => {
      const q = { type: "single", answer: ["A"] };
      expect(isOptionCorrect(q, "A. 选项1", 0)).toBe(true);
      expect(isOptionCorrect(q, "B. 选项2", 1)).toBe(false);
    });

    it("should match single choice by index letter even without prefix in option text", () => {
      const q = { type: "single", answer: ["B"] };
      expect(isOptionCorrect(q, "选项1", 0)).toBe(false);
      expect(isOptionCorrect(q, "选项2", 1)).toBe(true);
    });

    it("should match single choice by content text", () => {
      const q = { type: "single", answer: ["微积分"] };
      expect(isOptionCorrect(q, "A. 微积分", 0)).toBe(true);
      expect(isOptionCorrect(q, "微积分", 0)).toBe(true);
      expect(isOptionCorrect(q, "B. 线性代数", 1)).toBe(false);
    });

    it("should match multiple choice answers correctly", () => {
      const q = { type: "multiple", answer: ["A", "C"] };
      expect(isOptionCorrect(q, "A. 选项1", 0)).toBe(true);
      expect(isOptionCorrect(q, "B. 选项2", 1)).toBe(false);
      expect(isOptionCorrect(q, "C. 选项3", 2)).toBe(true);
      expect(isOptionCorrect(q, "D. 选项4", 3)).toBe(false);
    });

    it("should match judge question by Chinese or letter aliases", () => {
      const qTrue = { type: "judge", answer: ["正确"] };
      expect(isOptionCorrect(qTrue, "A. 正确", 0)).toBe(true);
      expect(isOptionCorrect(qTrue, "B. 错误", 1)).toBe(false);

      const qLetter = { type: "judge", answer: ["A"] };
      expect(isOptionCorrect(qLetter, "正确", 0)).toBe(true);
      expect(isOptionCorrect(qLetter, "错误", 1)).toBe(false);

      const qFalse = { type: "judge", answer: ["错"] };
      expect(isOptionCorrect(qFalse, "A. 对", 0)).toBe(false);
      expect(isOptionCorrect(qFalse, "B. 错", 1)).toBe(true);
    });
  });

  describe("formatQuestionAnswer", () => {
    it("should format fill / short / coding answers with commas", () => {
      expect(formatQuestionAnswer({ type: "fill", answer: ["x = 1", "y = 2"] })).toBe("x = 1，y = 2");
      expect(formatQuestionAnswer({ type: "short", answer: ["要点一", "要点二"] })).toBe("要点一，要点二");
    });

    it("should format choice letters", () => {
      expect(formatQuestionAnswer({ type: "single", answer: ["A. 微积分"] })).toBe("A");
      expect(formatQuestionAnswer({ type: "multiple", answer: ["A", "C"] })).toBe("A，C");
    });

    it("should format judge answers", () => {
      expect(formatQuestionAnswer({ type: "judge", answer: ["正确"] })).toBe("A");
      expect(formatQuestionAnswer({ type: "judge", answer: ["错误"] })).toBe("B");
    });
  });
});
