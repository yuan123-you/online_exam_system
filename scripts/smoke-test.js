const fs = require("fs");
const path = require("path");

const storeFile = path.join(__dirname, "..", "data", "store.json");
const PAPER_TAG = "comprehensive-paper-20260430";

const EXPECTED_BLUEPRINT = {
  single: 5,
  multiple: 5,
  judge: 5,
  fill: 5,
  short: 2,
  coding: 1,
};

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function countByType(questionIds, questionsById) {
  return questionIds.reduce((result, questionId) => {
    const type = questionsById.get(questionId)?.type || "unknown";
    result[type] = (result[type] || 0) + 1;
    return result;
  }, {});
}

function main() {
  const store = JSON.parse(fs.readFileSync(storeFile, "utf8"));
  const questions = Array.isArray(store.questions) ? store.questions : [];
  const papers = Array.isArray(store.papers) ? store.papers : [];
  const wrongBookEntries = Array.isArray(store.wrongBookEntries) ? store.wrongBookEntries : [];
  const questionsById = new Map(questions.map((item) => [item.id, item]));
  const questionIdSet = new Set(questions.map((item) => item.id));
  const titleSet = new Set(questions.map((item) => String(item.title || "").trim().replace(/\s+/g, " ")));

  assert(questions.length >= 500, `题库数量不足，当前仅有 ${questions.length} 道题。`);
  assert(questionIdSet.size === questions.length, "题目 ID 存在重复。");
  assert(titleSet.size === questions.length, "题干存在重复。");
  assert(Array.isArray(wrongBookEntries), "wrongBookEntries 数据结构不存在。");

  papers.forEach((paper) => {
    const scoreSum = (paper.questionIds || []).reduce((sum, questionId) => sum + Number(questionsById.get(questionId)?.score || 0), 0);
    assert(Number(paper.totalScore) === 100, `试卷 ${paper.name} 满分不是 100。`);
    assert(scoreSum === 100, `试卷 ${paper.name} 题目分值合计不是 100。`);
  });

  const comprehensivePapers = papers.filter((item) => item.sourceTag === PAPER_TAG);
  assert(comprehensivePapers.length === 10, `综合试卷数量不正确，当前为 ${comprehensivePapers.length} 套。`);

  const usedQuestionIds = new Set();
  comprehensivePapers.forEach((paper) => {
    assert(Number(paper.passScore) === 60, `试卷 ${paper.name} 及格线不是 60。`);
    assert((paper.questionIds || []).length === 23, `试卷 ${paper.name} 题量不是 23。`);
    const counts = countByType(paper.questionIds, questionsById);
    Object.entries(EXPECTED_BLUEPRINT).forEach(([type, count]) => {
      assert((counts[type] || 0) === count, `试卷 ${paper.name} 的 ${type} 题数量错误。`);
    });

    paper.questionIds.forEach((questionId) => {
      assert(questionsById.has(questionId), `试卷 ${paper.name} 引用了不存在的题目 ${questionId}。`);
      assert(!usedQuestionIds.has(questionId), `试卷 ${paper.name} 与其他综合卷重复使用了题目 ${questionId}。`);
      usedQuestionIds.add(questionId);
    });
  });

  console.log("Smoke test passed.");
  console.log(`questions=${questions.length}`);
  console.log(`papers=${papers.length}`);
  console.log(`comprehensivePapers=${comprehensivePapers.length}`);
  console.log(`uniqueUsedByComprehensive=${usedQuestionIds.size}`);
}

main();
