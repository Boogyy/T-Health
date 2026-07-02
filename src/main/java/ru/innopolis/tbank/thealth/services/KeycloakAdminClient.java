package ru.innopolis.tbank.thealth.services;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import ru.innopolis.tbank.thealth.config.KeycloakAdminProperties;

import java.util.UUID;

@Service
public class KeycloakAdminClient {

    private final RestClient restClient;
    private final KeycloakAdminProperties properties;

    public KeycloakAdminClient(KeycloakAdminProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }

    public void deleteUser(UUID keycloakUserId) {
        String serviceToken = getServiceToken();

        restClient.delete()
                .uri("/admin/realms/{realm}/users/{userId}",
                        properties.realm(),
                        keycloakUserId)
                .header("Authorization", "Bearer " + serviceToken)
                .retrieve()
                .toBodilessEntity();
    }

    private String getServiceToken() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());

        TokenResponse response = restClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("Failed to get Keycloak service token");
        }

        return response.accessToken();
    }

    private record TokenResponse(
            @JsonProperty("access_token")
            String accessToken
    ) {
    }
}