package ru.innopolis.tbank.thealth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import ru.innopolis.tbank.thealth.entities.DirectChatEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.repositories.DirectChatRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DirectChatControllerIT extends AbstractIntegrationTest {

    private static final UUID FIRST_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_ID = UUID.fromString("50000000-0000-0000-0000-000000000002");
    private static final UUID OUTSIDER_ID = UUID.fromString("50000000-0000-0000-0000-000000000003");

    @Autowired
    private DirectChatRepository directChatRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Создание личного чата возвращает собеседника")
    void createDirectChat_validRecipient_returnsCreated() throws Exception {
        persistUser(FIRST_ID, "chat-first");
        persistUser(SECOND_ID, "chat-second");

        mockMvc.perform(post("/api/direct-chats")
                        .with(jwtFor(FIRST_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientId": "%s"}
                                """.formatted(SECOND_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companionId").value(SECOND_ID.toString()))
                .andExpect(jsonPath("$.companionUsername").value("chat-second"));

        assertThat(directChatRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Пары A-B и B-A используют один личный чат")
    void createDirectChat_samePairTwice_returnsSameChatWithoutDuplicate() throws Exception {
        persistUser(FIRST_ID, "chat-first");
        persistUser(SECOND_ID, "chat-second");

        String firstResponse = mockMvc.perform(post("/api/direct-chats")
                        .with(jwtFor(FIRST_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientId": "%s"}
                                """.formatted(SECOND_ID)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondResponse = mockMvc.perform(post("/api/direct-chats")
                        .with(jwtFor(SECOND_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientId": "%s"}
                                """.formatted(FIRST_ID)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstChatId = objectMapper.readTree(firstResponse).get("id").asText();
        String secondChatId = objectMapper.readTree(secondResponse).get("id").asText();
        assertThat(secondChatId).isEqualTo(firstChatId);
        assertThat(directChatRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Нельзя создать личный чат с самим собой")
    void createDirectChat_withYourself_returnsBadRequest() throws Exception {
        persistUser(FIRST_ID, "chat-first");

        mockMvc.perform(post("/api/direct-chats")
                        .with(jwtFor(FIRST_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientId": "%s"}
                                """.formatted(FIRST_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Участники чата могут отправлять и читать сообщения")
    void sendAndReadMessage_chatParticipant_works() throws Exception {
        UserEntity first = persistUser(FIRST_ID, "chat-first");
        UserEntity second = persistUser(SECOND_ID, "chat-second");
        DirectChatEntity chat = saveChat(first, second);

        mockMvc.perform(post("/api/direct-chats/{id}/messages", chat.getId())
                        .with(jwtFor(FIRST_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "Привет! Как дела?"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chatId").value(chat.getId().toString()))
                .andExpect(jsonPath("$.senderId").value(FIRST_ID.toString()))
                .andExpect(jsonPath("$.content").value("Привет! Как дела?"));

        mockMvc.perform(get("/api/direct-chats/{id}/messages", chat.getId())
                        .with(jwtFor(SECOND_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("Привет! Как дела?"));
    }

    @Test
    @DisplayName("Посторонний пользователь не может читать чужой чат")
    void getMessages_outsider_returnsNotFound() throws Exception {
        UserEntity first = persistUser(FIRST_ID, "chat-first");
        UserEntity second = persistUser(SECOND_ID, "chat-second");
        persistUser(OUTSIDER_ID, "chat-outsider");
        DirectChatEntity chat = saveChat(first, second);

        mockMvc.perform(get("/api/direct-chats/{id}/messages", chat.getId())
                        .with(jwtFor(OUTSIDER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Пустое личное сообщение возвращает 400")
    void createMessage_blankContent_returnsBadRequest() throws Exception {
        UserEntity first = persistUser(FIRST_ID, "chat-first");
        UserEntity second = persistUser(SECOND_ID, "chat-second");
        DirectChatEntity chat = saveChat(first, second);

        mockMvc.perform(post("/api/direct-chats/{id}/messages", chat.getId())
                        .with(jwtFor(FIRST_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private DirectChatEntity saveChat(UserEntity first, UserEntity second) {
        UserEntity normalizedFirst = first.getKeycloakId().compareTo(second.getKeycloakId()) < 0
                ? first
                : second;
        UserEntity normalizedSecond = first.getKeycloakId().compareTo(second.getKeycloakId()) < 0
                ? second
                : first;

        DirectChatEntity chat = new DirectChatEntity();
        chat.setFirstUser(normalizedFirst);
        chat.setSecondUser(normalizedSecond);
        return directChatRepository.saveAndFlush(chat);
    }
}
