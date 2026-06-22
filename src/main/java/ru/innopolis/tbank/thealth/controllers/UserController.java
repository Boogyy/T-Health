package ru.innopolis.tbank.thealth.controllers;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.innopolis.tbank.thealth.dto.request.UpdateUserRequest;
import ru.innopolis.tbank.thealth.dto.response.UserResponse;
import ru.innopolis.tbank.thealth.services.UserService;

import java.util.UUID;

@RestController
@RequestMapping("api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }


    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            @AuthenticationPrincipal Jwt jwt
    ) {
        var result = userService.getCurrentUser(jwt);
        return ResponseEntity.ok(result);
    }




    @PatchMapping("/update/me")
    public ResponseEntity<UserResponse> updateCurrentUser(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserRequest request
            ) {

        var result = userService.updateCurrentUser(jwt, request);
        return ResponseEntity.ok(result);
    }





}
