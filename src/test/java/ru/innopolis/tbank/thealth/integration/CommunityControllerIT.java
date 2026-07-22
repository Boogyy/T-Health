package ru.innopolis.tbank.thealth.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import ru.innopolis.tbank.thealth.entities.CommunityEntity;
import ru.innopolis.tbank.thealth.entities.CommunityMemberEntity;
import ru.innopolis.tbank.thealth.entities.UserEntity;
import ru.innopolis.tbank.thealth.enums.CommunityRole;
import ru.innopolis.tbank.thealth.repositories.CommunityMemberRepository;
import ru.innopolis.tbank.thealth.repositories.CommunityRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommunityControllerIT extends AbstractIntegrationTest {

    private static final UUID OWNER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityMemberRepository communityMemberRepository;

    @Test
    @DisplayName("Создание сообщества автоматически добавляет владельца с ролью OWNER")
    void createCommunity_validRequest_createsOwnerMembership() throws Exception {
        persistUser(OWNER_ID, "community-owner");

        mockMvc.perform(post("/api/communities")
                        .with(jwtFor(OWNER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "communityName": "Бег по утрам",
                                  "description": "Сообщество любителей бега"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(OWNER_ID.toString()))
                .andExpect(jsonPath("$.communityName").value("Бег по утрам"))
                .andExpect(jsonPath("$.membersCount").value(1))
                .andExpect(jsonPath("$.currentUserMember").value(true));

        CommunityEntity community = communityRepository.findAll().get(0);
        CommunityMemberEntity ownerMembership = communityMemberRepository
                .findByCommunity_IdAndUser_KeycloakId(community.getId(), OWNER_ID)
                .orElseThrow();
        assertThat(ownerMembership.getRole()).isEqualTo(CommunityRole.OWNER);
    }

    @Test
    @DisplayName("Повторное название сообщества возвращает 409, а не 500")
    void createCommunity_duplicateName_returnsConflictNot500() throws Exception {
        UserEntity owner = persistUser(OWNER_ID, "community-owner");
        saveCommunity(owner, "Бег по утрам");

        mockMvc.perform(post("/api/communities")
                        .with(jwtFor(OWNER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "communityName": "бег ПО УТРАМ",
                                  "description": "Дубликат"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("Участник может вступить в сообщество и выйти из него")
    void joinAndLeaveCommunity_memberLifecycle_works() throws Exception {
        UserEntity owner = persistUser(OWNER_ID, "community-owner");
        persistUser(MEMBER_ID, "community-member");
        CommunityEntity community = saveCommunity(owner, "Велоклуб");

        mockMvc.perform(post("/api/communities/{id}/join", community.getId())
                        .with(jwtFor(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersCount").value(2))
                .andExpect(jsonPath("$.currentUserMember").value(true));

        assertThat(communityMemberRepository
                .existsByCommunity_IdAndUser_KeycloakId(community.getId(), MEMBER_ID))
                .isTrue();

        mockMvc.perform(delete("/api/communities/{id}/leave", community.getId())
                        .with(jwtFor(MEMBER_ID)))
                .andExpect(status().isNoContent());

        assertThat(communityMemberRepository
                .existsByCommunity_IdAndUser_KeycloakId(community.getId(), MEMBER_ID))
                .isFalse();
    }

    @Test
    @DisplayName("Не владелец не может изменить сообщество")
    void updateCommunity_nonOwner_returnsForbidden() throws Exception {
        UserEntity owner = persistUser(OWNER_ID, "community-owner");
        persistUser(MEMBER_ID, "community-member");
        CommunityEntity community = saveCommunity(owner, "Плавание");

        mockMvc.perform(patch("/api/communities/{id}", community.getId())
                        .with(jwtFor(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"communityName": "Чужое изменение"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Неучастник не может создать пост в сообществе")
    void createCommunityPost_nonMember_returnsForbidden() throws Exception {
        UserEntity owner = persistUser(OWNER_ID, "community-owner");
        persistUser(MEMBER_ID, "community-stranger");
        CommunityEntity community = saveCommunity(owner, "Йога");

        mockMvc.perform(post("/api/communities/{id}/posts/text", community.getId())
                        .with(jwtFor(MEMBER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Занятие",
                                  "content": "Кто идет сегодня?"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Неучастник не может получить список участников сообщества")
    void getCommunityMembers_nonMember_returnsForbidden() throws Exception {
        UserEntity owner = persistUser(OWNER_ID, "community-owner");
        persistUser(MEMBER_ID, "community-stranger");
        CommunityEntity community = saveCommunity(owner, "Закрытый клуб");

        mockMvc.perform(get("/api/communities/{id}/members", community.getId())
                        .with(jwtFor(MEMBER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Неучастник не может получить посты сообщества")
    void getCommunityPosts_nonMember_returnsForbidden() throws Exception {
        UserEntity owner = persistUser(OWNER_ID, "community-owner");
        persistUser(MEMBER_ID, "community-stranger");
        CommunityEntity community = saveCommunity(owner, "Закрытые посты");

        mockMvc.perform(get("/api/communities/{id}/posts", community.getId())
                        .with(jwtFor(MEMBER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Владелец не может выйти из собственного сообщества")
    void leaveCommunity_owner_returnsBadRequest() throws Exception {
        UserEntity owner = persistUser(OWNER_ID, "community-owner");
        CommunityEntity community = saveCommunity(owner, "Туризм");

        mockMvc.perform(delete("/api/communities/{id}/leave", community.getId())
                        .with(jwtFor(OWNER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private CommunityEntity saveCommunity(UserEntity owner, String name) {
        CommunityEntity community = new CommunityEntity();
        community.setOwner(owner);
        community.setCommunityName(name);
        community.setDescription("Тестовое сообщество");
        CommunityEntity saved = communityRepository.saveAndFlush(community);

        CommunityMemberEntity membership = new CommunityMemberEntity();
        membership.setCommunity(saved);
        membership.setUser(owner);
        membership.setRole(CommunityRole.OWNER);
        communityMemberRepository.saveAndFlush(membership);
        return saved;
    }
}
