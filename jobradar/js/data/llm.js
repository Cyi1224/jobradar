/**
 * data/llm.js — 大模型直连客户端（OpenAI 兼容 /chat/completions）
 * ─────────────────────────────────────────────
 * 供「无后端」场景下，前端直接调用 DeepSeek / 通义千问 / Kimi / OpenAI 等
 * 兼容接口。配置见 config.js 的 CONFIG.LLM。
 * 接好 Spring Boot 后端后建议改由后端代理（密钥不落前端）。
 */
import { CONFIG } from '../config.js';

/** 是否已配置可用的大模型（开关打开且填了密钥） */
export function llmEnabled() {
  const c = CONFIG.LLM;
  return !!(c && c.ENABLED && c.API_KEY && c.BASE_URL && c.MODEL);
}

/**
 * 发起一次对话补全。
 * @param {Array<{role:string, content:string}>} messages
 * @param {{temperature?:number, json?:boolean}} opts
 * @returns {Promise<string>} 模型回复的文本内容
 */
export async function chat(messages, { temperature = 0.7, json = false } = {}) {
  const { BASE_URL, API_KEY, MODEL } = CONFIG.LLM;
  const url = BASE_URL.replace(/\/+$/, '') + '/chat/completions';

  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${API_KEY}`,
    },
    body: JSON.stringify({
      model: MODEL,
      messages,
      temperature,
      ...(json ? { response_format: { type: 'json_object' } } : {}),
    }),
  });

  if (!res.ok) {
    const detail = await res.text().catch(() => '');
    throw new Error(`大模型请求失败 ${res.status}：${detail.slice(0, 200)}`);
  }
  const data = await res.json();
  return data?.choices?.[0]?.message?.content ?? '';
}

/** 发起对话并把回复解析为 JSON（自动剥离 ```json 围栏与多余文字） */
export async function chatJSON(messages, opts = {}) {
  const text = await chat(messages, { ...opts, json: true });
  return parseJSON(text);
}

function parseJSON(text) {
  const cleaned = String(text).trim()
    .replace(/^```(?:json)?\s*/i, '')
    .replace(/\s*```$/, '')
    .trim();
  try {
    return JSON.parse(cleaned);
  } catch {
    const m = cleaned.match(/[[{][\s\S]*[\]}]/); // 容错：截取第一段 JSON
    if (m) return JSON.parse(m[0]);
    throw new Error('大模型返回的内容不是合法 JSON：' + cleaned.slice(0, 120));
  }
}
