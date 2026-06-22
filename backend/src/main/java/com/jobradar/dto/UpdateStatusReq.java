package com.jobradar.dto;

import java.util.Map;

/**
 * 更新状态请求体：{ status, fields }
 * fields 是前端动态表单收集到的扁平键值（exam-note / int-note / oc-salary / fail-stage / note ...）。
 */
public record UpdateStatusReq(String status, Map<String, String> fields) {}
