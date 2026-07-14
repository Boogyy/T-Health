package ru.innopolis.tbank.thealth.unit.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.innopolis.tbank.thealth.dto.request.DirectChatCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.DirectMessageCreateRequest;
import ru.innopolis.tbank.thealth.dto.response.DirectChatResponse;
import ru.innopolis.tbank.thealth.dto.response.DirectMessageResponse;
import ru.innopolis.tbank.thealth.entities.DirectChatEntity;
import ru.innopolis.tbank.thealth.entities.DirectMessageEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.exceptions.DirectChatNotFoundException;
import ru.innopolis.tbank.thealth.exceptions.DirectChatWithYourselfException;
import ru.innopolis.tbank.thealth.mappers.DirectChatMapper;
import ru.innopolis.tbank.thealth.repositories.DirectChatRepository;
import ru.innopolis.tbank.thealth.repositories.DirectMessageRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;
import ru.innopolis.tbank.thealth.services.DirectChatService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectChatServiceTest {

    private static final UUID FIRST_ID = UUID.fromString("11111111-aaaa-aaaa-aaaa-111111111111");
    private static final UUID SECOND_ID = UUID.fromString("22222222-bbbb-bbbb-bbbb-222222222222");
    private static final UUID CHAT_ID = UUID.fromString("33333333-cccc-cccc-cccc-333333333333");
    private static final UUID MESSAGE_ID = UUID.fromString("44444444-dddd-dddd-dddd-444444444444");

    @Mock private DirectChatRepository directChatRepository;
    @Mock private DirectMessageRepository directMessageRepository;
    @Mock private UserRepository userRepository;
    @Mock private DirectChatMapper directChatMapper;

    @Test
    void createDirectChat_withYourself_throwsBadRequest() {
        assertThatThrownBy(() -> service().createDirectChat(
                new DirectChatCreateRequest(FIRST_ID), FIRST_ID))
                .isInstanceOf(DirectChatWithYourselfException.class);

        verifyNoInteractions(userRepository, directChatRepository);
    }

    @Test
    void createDirectChat_existingPair_returnsExistingChatWithoutSave() {
        UserEntity first = user(FIRST_ID, "first");
        UserEntity second = user(SECOND_ID, "second");
        DirectChatEntity chat = chat(first, second);
        DirectChatResponse expected = response(SECOND_ID, null);

        when(userRepository.findByKeycloakId(FIRST_ID)).thenReturn(Optional.of(first));
        when(userRepository.findByKeycloakId(SECOND_ID)).thenReturn(Optional.of(second));
        when(directChatRepository.findByFirstUser_KeycloakIdAndSecondUser_KeycloakId(FIRST_ID, SECOND_ID))
                .thenReturn(Optional.of(chat));
        when(directMessageRepository.findTopByChat_IdOrderBySentAtDesc(CHAT_ID)).thenReturn(Optional.empty());
        when(directChatMapper.toDirectChatResponse(chat, FIRST_ID, null)).thenReturn(expected);

        DirectChatResponse result = service().createDirectChat(
                new DirectChatCreateRequest(SECOND_ID), FIRST_ID);

        assertThat(result).isSameAs(expected);
        verify(directChatRepository, never()).save(any());
    }

    @Test
    void createDirectChat_newPair_normalizesUsersAndSavesChat() {
        UserEntity first = user(FIRST_ID, "first");
        UserEntity second = user(SECOND_ID, "second");
        DirectChatResponse expected = response(SECOND_ID, null);

        when(userRepository.findByKeycloakId(FIRST_ID)).thenReturn(Optional.of(first));
        when(userRepository.findByKeycloakId(SECOND_ID)).thenReturn(Optional.of(second));
        when(directChatRepository.findByFirstUser_KeycloakIdAndSecondUser_KeycloakId(FIRST_ID, SECOND_ID))
                .thenReturn(Optional.empty());
        when(directChatRepository.save(any(DirectChatEntity.class))).thenAnswer(invocation -> {
            DirectChatEntity saved = invocation.getArgument(0);
            saved.setId(CHAT_ID);
            saved.setCreatedAt(LocalDateTime.of(2026, 7, 14, 12, 0));
            return saved;
        });
        when(directMessageRepository.findTopByChat_IdOrderBySentAtDesc(CHAT_ID)).thenReturn(Optional.empty());
        when(directChatMapper.toDirectChatResponse(any(DirectChatEntity.class), eq(FIRST_ID), isNull()))
                .thenReturn(expected);

        DirectChatResponse result = service().createDirectChat(
                new DirectChatCreateRequest(SECOND_ID), FIRST_ID);

        ArgumentCaptor<DirectChatEntity> captor = ArgumentCaptor.forClass(DirectChatEntity.class);
        verify(directChatRepository).save(captor.capture());
        assertThat(captor.getValue().getFirstUser().getKeycloakId()).isEqualTo(FIRST_ID);
        assertThat(captor.getValue().getSecondUser().getKeycloakId()).isEqualTo(SECOND_ID);
        assertThat(result).isSameAs(expected);
    }

    @Test
    void getChatMessages_nonParticipant_throwsNotFound() {
        when(directChatRepository.findByIdAndParticipantId(CHAT_ID, FIRST_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getChatMessages(CHAT_ID, FIRST_ID))
                .isInstanceOf(DirectChatNotFoundException.class);

        verify(directMessageRepository, never()).findAllByChat_IdOrderBySentAtAsc(any());
    }

    @Test
    void getChatMessages_participant_returnsMessagesInRepositoryOrder() {
        DirectChatEntity chat = chat(user(FIRST_ID, "first"), user(SECOND_ID, "second"));
        DirectMessageEntity message = message(chat, chat.getFirstUser());
        DirectMessageResponse mapped = messageResponse();

        when(directChatRepository.findByIdAndParticipantId(CHAT_ID, FIRST_ID))
                .thenReturn(Optional.of(chat));
        when(directMessageRepository.findAllByChat_IdOrderBySentAtAsc(CHAT_ID))
                .thenReturn(List.of(message));
        when(directChatMapper.toDirectMessageResponse(message)).thenReturn(mapped);

        List<DirectMessageResponse> result = service().getChatMessages(CHAT_ID, FIRST_ID);

        assertThat(result).containsExactly(mapped);
    }

    @Test
    void createMessage_participant_savesSenderAndContent() {
        UserEntity first = user(FIRST_ID, "first");
        DirectChatEntity chat = chat(first, user(SECOND_ID, "second"));
        DirectMessageResponse mapped = messageResponse();

        when(directChatRepository.findByIdAndParticipantId(CHAT_ID, FIRST_ID))
                .thenReturn(Optional.of(chat));
        when(userRepository.findByKeycloakId(FIRST_ID)).thenReturn(Optional.of(first));
        when(directMessageRepository.save(any(DirectMessageEntity.class))).thenAnswer(invocation -> {
            DirectMessageEntity saved = invocation.getArgument(0);
            saved.setId(MESSAGE_ID);
            saved.setSentAt(LocalDateTime.of(2026, 7, 14, 12, 5));
            return saved;
        });
        when(directChatMapper.toDirectMessageResponse(any(DirectMessageEntity.class))).thenReturn(mapped);

        DirectMessageResponse result = service().createMessage(
                CHAT_ID,
                FIRST_ID,
                new DirectMessageCreateRequest("Привет!")
        );

        ArgumentCaptor<DirectMessageEntity> captor = ArgumentCaptor.forClass(DirectMessageEntity.class);
        verify(directMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getChat()).isSameAs(chat);
        assertThat(captor.getValue().getSender()).isSameAs(first);
        assertThat(captor.getValue().getContent()).isEqualTo("Привет!");
        assertThat(result).isSameAs(mapped);
    }

    private DirectChatService service() {
        return new DirectChatService(
                directChatRepository,
                directMessageRepository,
                userRepository,
                directChatMapper
        );
    }

    private UserEntity user(UUID id, String username) {
        UserEntity user = new UserEntity();
        user.setKeycloakId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        return user;
    }

    private DirectChatEntity chat(UserEntity first, UserEntity second) {
        DirectChatEntity chat = new DirectChatEntity();
        chat.setId(CHAT_ID);
        chat.setFirstUser(first);
        chat.setSecondUser(second);
        chat.setCreatedAt(LocalDateTime.of(2026, 7, 14, 12, 0));
        return chat;
    }

    private DirectMessageEntity message(DirectChatEntity chat, UserEntity sender) {
        DirectMessageEntity message = new DirectMessageEntity();
        message.setId(MESSAGE_ID);
        message.setChat(chat);
        message.setSender(sender);
        message.setContent("Привет!");
        message.setSentAt(LocalDateTime.of(2026, 7, 14, 12, 5));
        return message;
    }

    private DirectMessageResponse messageResponse() {
        return new DirectMessageResponse(
                MESSAGE_ID,
                CHAT_ID,
                FIRST_ID,
                "first",
                "Привет!",
                LocalDateTime.of(2026, 7, 14, 12, 5)
        );
    }

    private DirectChatResponse response(UUID companionId, DirectMessageResponse lastMessage) {
        return new DirectChatResponse(
                CHAT_ID,
                companionId,
                "second@example.com",
                "second",
                lastMessage,
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
    }
}
