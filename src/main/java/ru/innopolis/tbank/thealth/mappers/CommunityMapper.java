package ru.innopolis.tbank.thealth.mappers;

import org.springframework.stereotype.Component;
import ru.innopolis.tbank.thealth.dto.response.CommentResponse;
import ru.innopolis.tbank.thealth.dto.response.CommunityMemberResponse;
import ru.innopolis.tbank.thealth.dto.response.CommunityResponse;
import ru.innopolis.tbank.thealth.entities.CommentEntity;
import ru.innopolis.tbank.thealth.entities.CommunityEntity;
import ru.innopolis.tbank.thealth.entities.CommunityMemberEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;

@Component
public class CommunityMapper {

    public CommunityResponse toCommunityResponse(
            CommunityEntity community,
            long membersCount,
            boolean currentUserMember
    ) {
        return new CommunityResponse(
                community.getId(),
                community.getOwner().getKeycloakId(),
                community.getCommunityName(),
                community.getDescription(),
                membersCount,
                currentUserMember,
                community.getCreatedAt(),
                community.getUpdatedAt()
        );
    }

    public CommunityMemberResponse toCommunityMemberResponse(CommunityMemberEntity communityMember) {
        UserEntity user = communityMember.getUser();

        return new CommunityMemberResponse(
                user.getKeycloakId(),
                user.getUsername(),
                communityMember.getRole(),
                communityMember.getJoinedAt()
        );
    }

    public CommentResponse toCommentResponse(CommentEntity comment) {
        UserEntity author = comment.getAuthor();

        return new CommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                author.getKeycloakId(),
                author.getUsername(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}