package com.jobradar;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST 接口层测试：用 MockMvc 直接打 HTTP，验证状态码与响应结构。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApplicationControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void list_returnsSeededArray() throws Exception {
        mvc.perform(get("/api/applications"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$").isArray())
           .andExpect(jsonPath("$[0].co").exists())
           .andExpect(jsonPath("$[0].logs").isArray());
    }

    @Test
    void stats_hasTotalKey() throws Exception {
        mvc.perform(get("/api/applications/stats"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.全部").isNumber());
    }

    @Test
    void create_missingCo_returns400() throws Exception {
        mvc.perform(post("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pos\":\"只有岗位\"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void create_valid_returns201_andAutoLogsWhenSubmitted() throws Exception {
        mvc.perform(post("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"co\":\"测试公司\",\"pos\":\"测试岗位\",\"status\":\"已投递\"}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").isNumber())
           .andExpect(jsonPath("$.co").value("测试公司"))
           .andExpect(jsonPath("$.logs[0].s").value("已投递"));
    }

    @Test
    void otherEndpoints_areReachable() throws Exception {
        mvc.perform(get("/api/jobs")).andExpect(status().isOk())
           .andExpect(jsonPath("$[0].coType").exists());
        mvc.perform(get("/api/recommendations")).andExpect(status().isOk())
           .andExpect(jsonPath("$[0].match").isNumber());
        mvc.perform(get("/api/review/summary")).andExpect(status().isOk())
           .andExpect(jsonPath("$.rates").isArray());
        mvc.perform(get("/api/profile")).andExpect(status().isOk())
           .andExpect(jsonPath("$.name").exists());
        mvc.perform(get("/api/resumes")).andExpect(status().isOk())
           .andExpect(jsonPath("$[0].name").exists());
    }
}
