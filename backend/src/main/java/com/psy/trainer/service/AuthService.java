package com.psy.trainer.service;

import com.psy.trainer.domain.AppUser;
import com.psy.trainer.repo.AppUserRepository;
import com.psy.trainer.security.JwtService;
import com.psy.trainer.web.dto.Dtos;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public Dtos.LoginResponse login(Dtos.LoginRequest request) {
        if (request == null || request.username() == null || request.username().isBlank()
                || request.password() == null) {
            throw new BadRequestException("Укажите логин и пароль");
        }
        AppUser user = users.findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(() -> new UnauthorizedException("Неверный логин или пароль"));
        if (!user.isEnabled()) {
            throw new UnauthorizedException("Учётная запись отключена");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Неверный логин или пароль");
        }
        JwtService.IssuedToken token = jwtService.issue(user);
        return new Dtos.LoginResponse(
                token.accessToken(),
                "Bearer",
                token.expiresInSeconds(),
                user.getUsername(),
                user.roleSet().stream().map(Enum::name).sorted().toList()
        );
    }
}
