const fs = require("fs");
const path = require("path");

const storeFile = path.join(__dirname, "..", "data", "store.json");
const GENERATED_TAG = "generated-bank-20260430";
const COMPREHENSIVE_TAG = "comprehensive-paper-20260430";
const FULL_SCORE = 100;
const PASS_SCORE = 60;
const TOTAL_COMPREHENSIVE_PAPERS = 10;

const SCORE_BY_TYPE = {
  single: 4,
  multiple: 4,
  judge: 2,
  fill: 4,
  short: 10,
  coding: 10,
};

const BLUEPRINT = {
  single: 5,
  multiple: 5,
  judge: 5,
  fill: 5,
  short: 2,
  coding: 1,
};

function readStore() {
  return JSON.parse(fs.readFileSync(storeFile, "utf8"));
}

function writeStore(store) {
  fs.writeFileSync(storeFile, JSON.stringify(store, null, 2), "utf8");
}

function getQuestionNumber(id) {
  const match = String(id || "").match(/(\d+)/);
  return match ? Number(match[1]) : Number.MAX_SAFE_INTEGER;
}

function sortQuestions(left, right) {
  const tagOrder = String(left.sourceTag || "").localeCompare(String(right.sourceTag || ""));
  if (tagOrder !== 0) return tagOrder;
  return getQuestionNumber(left.id) - getQuestionNumber(right.id) || String(left.id).localeCompare(String(right.id));
}

function pickQuestions(bucket, paperIndex, count) {
  const selected = [];
  for (let offset = 0; offset < count; offset += 1) {
    const question = bucket[paperIndex + offset * TOTAL_COMPREHENSIVE_PAPERS];
    if (!question) {
      throw new Error(`题量不足，无法为第 ${paperIndex + 1} 套试卷抽取 ${count} 道题。`);
    }
    selected.push(question.id);
  }
  return selected;
}

function rebuildComprehensivePapers(store) {
  const generatedQuestions = store.questions.filter((item) => item.sourceTag === GENERATED_TAG);
  const buckets = Object.fromEntries(
    Object.keys(BLUEPRINT).map((type) => [type, generatedQuestions.filter((item) => item.type === type).sort(sortQuestions)])
  );

  return store.papers.map((paper) => {
    if (paper.sourceTag !== COMPREHENSIVE_TAG) {
      return paper;
    }
    const paperIndex = Math.max(0, Number(String(paper.id).match(/(\d+)$/)?.[1] || 1) - 1);
    const questionIds = Object.entries(BLUEPRINT).flatMap(([type, count]) => pickQuestions(buckets[type], paperIndex, count));
    return {
      ...paper,
      totalScore: FULL_SCORE,
      passScore: PASS_SCORE,
      questionIds,
    };
  });
}

function rebuildGeneralPaper(store, paper, reservedQuestionIds) {
  const selected = [];
  const counts = Object.fromEntries(Object.keys(BLUEPRINT).map((type) => [type, 0]));
  const questionById = new Map(store.questions.map((item) => [item.id, item]));

  (paper.questionIds || []).forEach((id) => {
    const question = questionById.get(id);
    if (!question || reservedQuestionIds.has(id) || !Object.prototype.hasOwnProperty.call(BLUEPRINT, question.type)) {
      return;
    }
    if (counts[question.type] < BLUEPRINT[question.type]) {
      selected.push(id);
      counts[question.type] += 1;
    }
  });

  Object.entries(BLUEPRINT).forEach(([type, targetCount]) => {
    if (counts[type] >= targetCount) {
      return;
    }
    const candidates = store.questions
      .filter((item) => item.type === type && !reservedQuestionIds.has(item.id) && !selected.includes(item.id))
      .sort(sortQuestions);
    while (counts[type] < targetCount) {
      const question = candidates.shift();
      if (!question) {
        throw new Error(`试卷 ${paper.name} 缺少可用的 ${type} 题。`);
      }
      selected.push(question.id);
      counts[type] += 1;
    }
  });

  selected.forEach((id) => reservedQuestionIds.add(id));
  return {
    ...paper,
    totalScore: FULL_SCORE,
    passScore: PASS_SCORE,
    questionIds: selected,
  };
}

function assertPaperScore(store) {
  const questionById = new Map(store.questions.map((item) => [item.id, item]));
  store.papers.forEach((paper) => {
    const sum = (paper.questionIds || []).reduce((total, id) => total + Number(questionById.get(id)?.score || 0), 0);
    if (sum !== FULL_SCORE || Number(paper.totalScore) !== FULL_SCORE) {
      throw new Error(`试卷 ${paper.name} 满分不是 ${FULL_SCORE}，当前题目合计 ${sum}，记录总分 ${paper.totalScore}。`);
    }
  });
}

function syncSubmissionFullScores(store) {
  const questionById = new Map(store.questions.map((item) => [item.id, item]));
  store.submissions = (store.submissions || []).map((submission) => {
    const answerDetail = (submission.answerDetail || []).map((detail) => {
      const fullScore = Number(questionById.get(detail.questionId)?.score ?? detail.fullScore ?? 0);
      return {
        ...detail,
        fullScore,
        score: Math.min(Number(detail.score || 0), fullScore),
      };
    });
    const currentScore = answerDetail.reduce((sum, item) => sum + Number(item.score || 0), 0);
    return {
      ...submission,
      answerDetail,
      autoScore: Math.min(Number(submission.autoScore ?? currentScore), FULL_SCORE),
      finalScore: Math.min(Number(submission.finalScore ?? currentScore), FULL_SCORE),
    };
  });
}

function main() {
  const store = readStore();
  store.questions = Array.isArray(store.questions) ? store.questions : [];
  store.papers = Array.isArray(store.papers) ? store.papers : [];
  store.submissions = Array.isArray(store.submissions) ? store.submissions : [];
  store.logs = Array.isArray(store.logs) ? store.logs : [];

  store.questions = store.questions.map((question) => ({
    ...question,
    score: SCORE_BY_TYPE[question.type] || Number(question.score || 0),
  }));

  store.papers = rebuildComprehensivePapers(store);
  const reservedQuestionIds = new Set(
    store.papers
      .filter((paper) => paper.sourceTag === COMPREHENSIVE_TAG)
      .flatMap((paper) => paper.questionIds || [])
  );
  store.papers = store.papers.map((paper) =>
    paper.sourceTag === COMPREHENSIVE_TAG ? paper : rebuildGeneralPaper(store, paper, reservedQuestionIds)
  );

  syncSubmissionFullScores(store);
  assertPaperScore(store);

  store.logs.unshift({
    id: `log-normalize-paper-score-${Date.now()}`,
    actorId: "teacher-1",
    action: "统一试卷满分",
    detail: JSON.stringify({ totalScore: FULL_SCORE, passScore: PASS_SCORE, blueprint: BLUEPRINT, scoreByType: SCORE_BY_TYPE }),
    time: new Date().toISOString(),
  });

  writeStore(store);
  console.log(`Normalized ${store.papers.length} papers to ${FULL_SCORE} points.`);
}

main();
