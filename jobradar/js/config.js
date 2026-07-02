/**
 * 全局配置
 * ─────────────────────────────────────────────
 * 数据源开关：
 *   USE_MOCK = true   →  使用本地内存假数据（mock.js），无需后端即可运行
 *   USE_MOCK = false  →  通过 fetch 调用真实后端（http.js）
 *
 * 接好 Spring Boot 后端后，只需把 USE_MOCK 改成 false，
 * 并把 API_BASE 指向后端地址，整个前端无需改动其它代码。
 */
export const CONFIG = {
  USE_MOCK: false,
  API_BASE: '/api',

  /** 百度统计 ID — 在 tongji.baidu.com 注册站点后获取，填在这里即可生效 */
  BAIDU_TONGJI_ID: '7012d6646f9652d2c9521cb73787ec95',

  /**
   * ── 大模型直连（OpenAI 兼容接口）──
   * 【当前状态：休眠】简历评分、模拟面试两个 AI 功能已移除，暂无页面调用此配置；
   * 保留这套接入（config + data/llm.js + server.js 的 /api/llm 代理）以备后续 AI 功能复用。
   * 兼容 DeepSeek / 通义千问(DashScope) / Kimi(Moonshot) / OpenAI 等。
   *
   * 使用方法：
   *   1) 把 API_KEY 填成你的密钥；
   *   2) 按所用服务商改 BASE_URL 和 MODEL（见下方备选）；
   *   3) 把 ENABLED 改成 true。
   * 优先级：ENABLED 为 true 时走真实大模型；否则回退本地 mock。
   *
   * ⚠️ 安全提示：密钥写在前端会暴露给浏览器，仅适合本地/个人演示。
   *    上线请改用 Spring Boot 后端代理（USE_MOCK=false，由后端持有密钥）。
   *
   * 备选服务商：
   *   DeepSeek   BASE_URL: 'https://api.deepseek.com/v1'                              MODEL: 'deepseek-chat'
   *   通义千问    BASE_URL: 'https://dashscope.aliyuncs.com/compatible-mode/v1'        MODEL: 'qwen-plus'
   *   Kimi       BASE_URL: 'https://api.moonshot.cn/v1'                               MODEL: 'moonshot-v1-8k'
   *   OpenAI     BASE_URL: 'https://api.openai.com/v1'                                MODEL: 'gpt-4o-mini'
   */
  LLM: {
    ENABLED: true,                             // 已接入 DeepSeek
    BASE_URL: '/api/llm/v1',                   // 走本地 server.js 代理（同源，密钥在后端注入）
    MODEL: 'deepseek-chat',                    // DeepSeek 对话模型
    API_KEY: 'via-proxy',                      // 占位：真实密钥由 server.js 通过 DEEPSEEK_API_KEY 注入
  },
};
