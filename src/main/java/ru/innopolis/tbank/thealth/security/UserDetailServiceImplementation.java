package ru.innopolis.tbank.thealth.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import ru.innopolis.tbank.thealth.repositories.UserRepository;

import java.util.NoSuchElementException;

public class UserDetailServiceImplementation implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByE(username).map(AppUserDetails::new).orElseThrow(() -> new NoSuchElementException());
    }
}
