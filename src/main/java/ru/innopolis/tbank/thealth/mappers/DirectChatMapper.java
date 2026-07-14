package ru.innopolis.tbank.thealth.mappers;

import org.springframework.stereotype.Component;
import ru.innopolis.tbank.thealth.dto.response.DirectChatResponse;
import ru.innopolis.tbank.thealth.dto.response.DirectMessageResponse;
import ru.innopolis.tbank.thealth.entities.DirectChatEntity;
import ru.innopolis.tbank.thealth.entities.DirectMessageEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;

import java.util.UUID;

@Component
public class DirectChatMapper {

    public DirectChatResponse toDirectChatResponse(
            DirectChatEntity chat,
            UUID currentUserId,
            DirectMessageResponse lastMessage
    ) {
        UserEntity companion = getCompanion(chat, currentUserId);

        return new DirectChatResponse(
                chat.getId(),
                companion.getKeycloakId(),
                companion.getEmail(),
                companion.getUsername(),
                lastMessage,
                chat.getCreatedAt()
        );
    }

    public DirectMessageResponse toDirectMessageResponse(DirectMessageEntity message) {
        UserEntity sender = message.getSender();

        return new DirectMessageResponse(
                message.getId(),
                message.getChat().getId(),
                sender.getKeycloakId(),
                sender.getUsername(),
                message.getContent(),
                message.getSentAt()
        );
    }

    private UserEntity getCompanion(DirectChatEntity chat, UUID currentUserId) {
        if (chat.getFirstUser().getKeycloakId().equals(currentUserId)) {
            return chat.getSecondUser();
        }

        return chat.getFirstUser();
    }
}