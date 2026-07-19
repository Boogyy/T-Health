package ru.innopolis.tbank.thealth.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.innopolis.tbank.thealth.entities.DirectChatEntity;
import ru.innopolis.tbank.thealth.repositories.DirectChatRepository;
import ru.innopolis.tbank.thealth.repositories.DirectMessageRepository;

import java.util.List;
import java.util.UUID;

@Service
public class DirectChatDeletionService {

    private final DirectChatRepository directChatRepository;
    private final DirectMessageRepository directMessageRepository;

    public DirectChatDeletionService(
            DirectChatRepository directChatRepository,
            DirectMessageRepository directMessageRepository
    ) {
        this.directChatRepository = directChatRepository;
        this.directMessageRepository = directMessageRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAllChatsByUser(UUID userId) {
        List<DirectChatEntity> chats =
                directChatRepository.findAllByParticipantId(userId);

        for (DirectChatEntity chat : chats) {
            directMessageRepository.deleteAllByChat_Id(chat.getId());
        }

        directChatRepository.deleteAll(chats);
    }
}