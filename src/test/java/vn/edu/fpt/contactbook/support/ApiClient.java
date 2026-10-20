package vn.edu.fpt.contactbook.support;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gọi REST API trong test tích hợp bằng token thật lấy từ /api/v1/auth/login (mật khẩu chung của dữ liệu mẫu). */
public final class ApiClient {

    private final MockMvc mockMvc;
    private final JsonMapper jsonMapper;

    public ApiClient(MockMvc mockMvc, JsonMapper jsonMapper) {
        this.mockMvc = mockMvc;
        this.jsonMapper = jsonMapper;
    }

    public String accessToken(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("phone", phone,
                                "password", TestAccounts.PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return json(result).get("accessToken").asString();
    }

    /** Gắn header Authorization: Bearer của tài khoản phone vào request rồi thực hiện. */
    public ResultActions perform(AbstractMockHttpServletRequestBuilder<?> request, String phone) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(phone)));
    }

    public JsonNode json(ResultActions actions) throws Exception {
        return json(actions.andReturn());
    }

    public JsonNode json(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    public static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
