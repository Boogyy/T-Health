package ru.innopolis.tbank.thealth.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.dto.request.DirectChatCreateRequest;
import ru.innopolis.tbank.thealth.dto.request.DirectMessageCreateRequest;
import ru.innopolis.tbank.thealth.dto.response.DirectChatResponse;
import ru.innopolis.tbank.thealth.dto.response.DirectMessageResponse;
import ru.innopolis.tbank.thealth.entities.DirectChatEntity;
import ru.innopolis.tbank.thealth.entities.DirectMessageEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.exceptions.DirectChatNotFoundException;
import ru.innopolis.tbank.thealth.exceptions.DirectChatWithYourselfException;
import ru.innopolis.tbank.thealth.exceptions.UserNotFoundException;
import ru.innopolis.tbank.thealth.mappers.DirectChatMapper;
import ru.innopolis.tbank.thealth.repositories.DirectChatRepository;
import ru.innopolis.tbank.thealth.repositories.DirectMessageRepository;
import ru.innopolis.tbank.thealth.repositories.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
public class DirectChatService {

    private final DirectChatRepository directChatRepository;
    private final DirectMessageRepository directMessageRepository;
    private final UserRepository userRepository;
    private final DirectChatMapper directChatMapper;

    public DirectChatService(DirectChatRepository directChatRepository,
                             DirectMessageRepository directMessageRepository,
                             UserRepository userRepository,
                             DirectChatMapper directChatMapper) {
        this.directChatRepository = directChatRepository;
        this.directMessageRepository = directMessageRepository;
        this.userRepository = userRepository;
        this.directChatMapper = directChatMapper;
    }

    @Transactional(readOnly = true)
    public List<DirectChatResponse> getCurrentUserChats(UUID currentUserId) {
        return directChatRepository.findAllByParticipantId(currentUserId)
                .stream()
                .map(chat -> toDirectChatResponse(chat, currentUserId))
                .toList();
    }

    @Transactional
    public DirectChatResponse createDirectChat(DirectChatCreateRequest request, UUID currentUserId) {
        UUID recipientId = request.recipientId();

        if (currentUserId.equals(recipientId)) {
            throw new DirectChatWithYourselfException();
        }

        UserEntity currentUser = findUser(currentUserId);
        UserEntity recipient = findUser(recipientId);

        UserEntity firstUser = currentUserId.compareTo(recipientId) < 0 ? currentUser : recipient;
        UserEntity secondUser = currentUserId.compareTo(recipientId) < 0 ? recipient : currentUser;

        return directChatRepository
                .findByFirstUser_KeycloakIdAndSecondUser_KeycloakId(
                        firstUser.getKeycloakId(),
                        secondUser.getKeycloakId()
                )
                .map(existingChat -> toDirectChatResponse(existingChat, currentUserId))
                .orElseGet(() -> {
                    DirectChatEntity chat = new DirectChatEntity();
                    chat.setFirstUser(firstUser);
                    chat.setSecondUser(secondUser);

                    DirectChatEntity savedChat = directChatRepository.save(chat);

                    return toDirectChatResponse(savedChat, currentUserId);
                });
    }

    @Transactional(readOnly = true)
    public List<DirectMessageResponse> getChatMessages(UUID chatId, UUID currentUserId) {
        findChatForCurrentUser(chatId, currentUserId);

        return directMessageRepository.findAllByChat_IdOrderBySentAtAsc(chatId)
                .stream()
                .map(directChatMapper::toDirectMessageResponse)
                .toList();
    }

    @Transactional
    public DirectMessageResponse createMessage(
            UUID chatId,
            UUID currentUserId,
            DirectMessageCreateRequest request
    ) {
        DirectChatEntity chat = findChatForCurrentUser(chatId, currentUserId);
        UserEntity sender = findUser(currentUserId);

        DirectMessageEntity message = new DirectMessageEntity();
        message.setChat(chat);
        message.setSender(sender);
        message.setContent(request.content());

        DirectMessageEntity savedMessage = directMessageRepository.save(message);

        return directChatMapper.toDirectMessageResponse(savedMessage);
    }

    private DirectChatEntity findChatForCurrentUser(UUID chatId, UUID currentUserId) {
        return directChatRepository.findByIdAndParticipantId(chatId, currentUserId)
                .orElseThrow(() -> new DirectChatNotFoundException(chatId));
    }

    private UserEntity findUser(UUID userId) {
        return userRepository.findByKeycloakId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private DirectChatResponse toDirectChatResponse(DirectChatEntity chat, UUID currentUserId) {
        DirectMessageResponse lastMessage = directMessageRepository
                .findTopByChat_IdOrderBySentAtDesc(chat.getId())
                .map(directChatMapper::toDirectMessageResponse)
                .orElse(null);

        return directChatMapper.toDirectChatResponse(chat, currentUserId, lastMessage);
    }
}