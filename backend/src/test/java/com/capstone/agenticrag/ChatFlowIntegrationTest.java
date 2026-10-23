package com.capstone.agenticrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.capstone.agenticrag.security.UserPrincipal;
import com.capstone.agenticrag.support.IntegrationTestBase;
import com.capstone.agenticrag.user.UserRepository;

class ChatFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    UserRepository users;

    // ------------------------------------------------------------------ helpers

    private UserPrincipal register(String email) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test User\",\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated());
        return UserPrincipal.from(users.findByEmail(email).orElseThrow());
    }

    private static String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private MockHttpServletRequestBuilder as(UserPrincipal p, MockHttpServletRequestBuilder b) {
        return b.with(user(p)).with(csrf());
    }

    private String createConversation(UserPrincipal p, String mode) throws Exception {
        String body = mvc.perform(as(p, post("/api/conversations")).contentType(MediaType.APPLICATION_JSON)
                        .content(mode == null ? "{}" : "{\"mode\":\"" + mode + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String send(UserPrincipal p, String conversationId, String content) throws Exception {
        return mvc.perform(as(p, post("/api/chat/" + conversationId + "/messages")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ auth & security

    @Test
    void sessionLoginAndCsrfAreEnforced() throws Exception {
        String email = uniqueEmail();
        register(email);

        mvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-pass1\"}"))
                .andExpect(status().isUnauthorized());

        MvcResult login = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email)).andReturn();
        Cookie session = login.getResponse().getCookie("ARA_SESSION");
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void systemStatusIsPublic() throws Exception {
        mvc.perform(get("/api/system/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.chatAvailable").value(true))
                .andExpect(jsonPath("$.webSearchAvailable").value(false));
    }

    @Test
    void adminEndpointsRequireAdminRole() throws Exception {
        UserPrincipal plain = register(uniqueEmail());
        mvc.perform(as(plain, get("/api/admin/stats"))).andExpect(status().isForbidden());

        UserPrincipal admin = users.findByEmail("admin@example.com").map(UserPrincipal::from)
                .orElseGet(() -> {
                    try {
                        return register("admin@example.com");
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
        mvc.perform(as(admin, get("/api/admin/stats"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isNumber());
        mvc.perform(as(admin, get("/api/admin/executions"))).andExpect(status().isOk());
        mvc.perform(as(admin, patch("/api/admin/users/" + admin.id())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}")).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ chat

    @Test
    void sendsMessagePersistsBothSidesAndTitlesTheConversation() throws Exception {
        UserPrincipal me = register(uniqueEmail());
        String id = createConversation(me, "CHAT");

        String body = send(me, id, "Hello there, assistant");
        assertThat(JsonPath.<String>read(body, "$.assistantMessage.content")).isEqualTo("You said: Hello there, assistant");
        assertThat(JsonPath.<String>read(body, "$.assistantMessage.status")).isEqualTo("COMPLETED");
        assertThat(JsonPath.<String>read(body, "$.userMessage.role")).isEqualTo("USER");

        mvc.perform(as(me, get("/api/conversations/" + id))).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hello there, assistant"))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"));
        mvc.perform(as(me, get("/api/conversations").param("q", "hello"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id));
    }

    @Test
    void regenerateReplacesTheLastReply() throws Exception {
        UserPrincipal me = register(uniqueEmail());
        String id = createConversation(me, "CHAT");
        String first = send(me, id, "Tell me something");
        String firstReplyId = JsonPath.read(first, "$.assistantMessage.id");

        String regenerated = mvc.perform(as(me, post("/api/chat/" + id + "/messages"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"regenerate\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(regenerated, "$.assistantMessage.id")).isNotEqualTo(firstReplyId);

        mvc.perform(as(me, get("/api/conversations/" + id)))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].id").value(JsonPath.<String>read(regenerated, "$.assistantMessage.id")));
    }

    @Test
    void rejectsInvalidMessages() throws Exception {
        UserPrincipal me = register(uniqueEmail());
        String id = createConversation(me, null);
        mvc.perform(as(me, post("/api/chat/" + id + "/messages")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"   \"}")).andExpect(status().isBadRequest());
        mvc.perform(as(me, post("/api/chat/" + id + "/messages")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + "x".repeat(8001) + "\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(as(me, post("/api/chat/" + id + "/messages")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"regenerate\":true}")).andExpect(status().isBadRequest());
    }

    @Test
    void streamsServerSentEvents() throws Exception {
        UserPrincipal me = register(uniqueEmail());
        String id = createConversation(me, "CHAT");

        MvcResult result = mvc.perform(as(me, post("/api/chat/" + id + "/stream"))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"content\":\"Stream this please\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        result.getAsyncResult(30_000);
        String events = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(events).contains("event:meta", "event:status", "event:content", "event:sources", "event:done");
        assertThat(events.indexOf("event:meta")).isLessThan(events.indexOf("event:content"));
        assertThat(events.indexOf("event:content")).isLessThan(events.indexOf("event:done"));
        assertThat(events).contains("You said: Stream this please").doesNotContain("event:error");
    }

    // ------------------------------------------------------------------ documents & citations

    @Test
    void answersFromUploadedDocumentsWithValidatedCitations() throws Exception {
        UserPrincipal me = register(uniqueEmail());
        MockMultipartFile file = new MockMultipartFile("file", "project-notes.txt", "text/plain",
                ("Project notes.\n\nThe launch code name is BLUEBIRD. The launch is planned for the spring.\n\n"
                        + "Unrelated: the cafeteria serves soup on Fridays.").getBytes(StandardCharsets.UTF_8));
        String uploaded = mvc.perform(as(me, multipart("/api/documents").file(file)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String documentId = JsonPath.read(uploaded, "$.id");
        awaitReady(me, documentId);

        String id = createConversation(me, "DOCUMENTS");
        String body = send(me, id, "What do my documents say about the launch code name?");

        String content = JsonPath.read(body, "$.assistantMessage.content");
        assertThat(content).contains("BLUEBIRD [1]").doesNotContain("[42]");
        assertThat(JsonPath.<Boolean>read(body, "$.assistantMessage.usedDocuments")).isTrue();
        List<String> types = JsonPath.read(body, "$.assistantMessage.sources[*].type");
        assertThat(types).containsOnly("DOCUMENT");
        assertThat(JsonPath.<String>read(body, "$.assistantMessage.sources[0].title")).isEqualTo("project-notes.txt");
        assertThat(JsonPath.<Boolean>read(body, "$.assistantMessage.sources[0].cited")).isTrue();

        String messageId = JsonPath.read(body, "$.assistantMessage.id");
        mvc.perform(as(me, get("/api/messages/" + messageId + "/sources"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(documentId));
    }

    private void awaitReady(UserPrincipal me, String documentId) throws Exception {
        for (int i = 0; i < 100; i++) {
            String s = mvc.perform(as(me, get("/api/documents/" + documentId + "/status")))
                    .andReturn().getResponse().getContentAsString();
            String status = JsonPath.read(s, "$.status");
            if ("READY".equals(status)) {
                return;
            }
            assertThat(status).isNotEqualTo("FAILED");
            Thread.sleep(100);
        }
        throw new AssertionError("Document was not processed in time");
    }

    // ------------------------------------------------------------------ memory

    @Test
    void remembersUserStatementsAndUsesThemLater() throws Exception {
        UserPrincipal me = register(uniqueEmail());
        String id = createConversation(me, "CHAT");
        send(me, id, "My name is Priya. Remember that I prefer concise answers.");

        String memories = mvc.perform(as(me, get("/api/memories"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> contents = JsonPath.read(memories, "$.items[*].content");
        assertThat(contents).contains("User's name is Priya", "User prefers concise answers");
        assertThat(chatModel.lastSystemPrompt()).contains("Saved to memory");

        // A later conversation sees the stored memories in its prompt.
        String other = createConversation(me, "CHAT");
        send(me, other, "What is my name and how should you answer?");
        assertThat(chatModel.lastSystemPrompt()).contains("User's name is Priya");

        // Contradiction: the newer name replaces the older one instead of accumulating.
        send(me, other, "My name is Priya Sharma.");
        memories = mvc.perform(as(me, get("/api/memories"))).andReturn().getResponse().getContentAsString();
        contents = JsonPath.read(memories, "$.items[*].content");
        assertThat(contents).contains("User's name is Priya Sharma").doesNotContain("User's name is Priya");
    }

    @Test
    void secretsAreNeverStoredAndDisabledMemoryStoresNothing() throws Exception {
        UserPrincipal me = register(uniqueEmail());
        String id = createConversation(me, "CHAT");
        send(me, id, "Remember that my password is hunter2");
        String memories = mvc.perform(as(me, get("/api/memories"))).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(memories, "$.items")).isEmpty();

        mvc.perform(as(me, patch("/api/memory/settings")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}")).andExpect(status().isOk());
        send(me, id, "My name is Rahul.");
        memories = mvc.perform(as(me, get("/api/memories"))).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(memories, "$.items")).isEmpty();
        assertThat(chatModel.lastSystemPrompt()).contains("memory is turned OFF");
    }

    // ------------------------------------------------------------------ isolation & feedback

    @Test
    void usersCannotSeeOrTouchEachOthersData() throws Exception {
        UserPrincipal alice = register(uniqueEmail());
        UserPrincipal bob = register(uniqueEmail());
        String id = createConversation(alice, "CHAT");
        String body = send(alice, id, "Alice's private note");
        String messageId = JsonPath.read(body, "$.assistantMessage.id");

        mvc.perform(as(bob, get("/api/conversations/" + id))).andExpect(status().isNotFound());
        mvc.perform(as(bob, post("/api/chat/" + id + "/messages")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hi\"}")).andExpect(status().isNotFound());
        mvc.perform(as(bob, post("/api/chat/" + id + "/cancel"))).andExpect(status().isNotFound());
        mvc.perform(as(bob, get("/api/messages/" + messageId + "/sources"))).andExpect(status().isNotFound());
        mvc.perform(as(bob, put("/api/messages/" + messageId + "/feedback")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"DOWN\"}")).andExpect(status().isNotFound());
        mvc.perform(as(bob, delete("/api/conversations/" + id))).andExpect(status().isNotFound());
        mvc.perform(as(bob, get("/api/conversations").param("q", "private")))
                .andExpect(jsonPath("$.items.length()").value(0));

        mvc.perform(as(alice, put("/api/messages/" + messageId + "/feedback")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"UP\",\"comment\":\"Nice\"}")).andExpect(status().isOk());
        mvc.perform(as(alice, get("/api/conversations/" + id)))
                .andExpect(jsonPath("$.messages[1].feedback").value("UP"));
        mvc.perform(as(alice, delete("/api/conversations/" + id))).andExpect(status().isNoContent());
        mvc.perform(as(alice, get("/api/conversations/" + id))).andExpect(status().isNotFound());
    }
}
