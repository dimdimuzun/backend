package com.provedcode.user.controller;

import com.provedcode.talent.model.entity.Talent;
import com.provedcode.talent.repo.db.TalentEntityRepository;
import com.provedcode.user.model.dto.RegistrationDTO;
import com.provedcode.user.model.entity.UserAuthority;
import com.provedcode.user.model.entity.UserInfo;
import com.provedcode.user.repo.AuthorityRepository;
import com.provedcode.user.repo.UserAuthorityRepository;
import com.provedcode.user.repo.UserInfoRepository;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.MINUTES;

@RestController
@AllArgsConstructor
@Slf4j
@RequestMapping("/api")
public class AuthenticationController {
    JwtEncoder jwtEncoder;
    UserInfoRepository userInfoRepository;
    TalentEntityRepository talentEntityRepository;
    UserAuthorityRepository userAuthorityRepository;
    AuthorityRepository authorityRepository;
    PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    String login(Authentication authentication) {
        log.info("=== POST /login === auth.name = {}", authentication.getName());
        log.info("=== POST /login === auth = {}", authentication);
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                                 .issuer("self")
                                 .issuedAt(now)
                                 .expiresAt(now.plus(5, MINUTES))
                                 .subject(authentication.getName())
                                 .claim("scope", createScope(authentication))
                                 .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    @PostMapping("/register")
    String register(@RequestBody @Valid RegistrationDTO user) {
        if (userInfoRepository.existsByLogin(user.login())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                              String.format("user with login = {%s} already exists", user.login()));
        }
        Talent talent = talentEntityRepository.save(Talent.builder()
                                                          .firstName(user.firstName())
                                                          .lastName(user.lastName())
                                                          .specialization(user.specialization())
                                                          .build());

        UserInfo userInfo = UserInfo.builder()
                                    .userId(talent.getId())
                                    .login(user.login())
                                    .password(passwordEncoder.encode(user.password()))
                                    .build();
        UserAuthority userAuthority = UserAuthority.builder().userInfo(userInfo)
                                                   .authority(authorityRepository.findById(1L).orElseThrow(
                                                           () -> new ResponseStatusException(
                                                                   HttpStatus.BAD_REQUEST,
                                                                   "user didn't created"))).build();
        userInfo.setUserAuthorities(Set.of(userAuthority));
        userAuthority.setUserInfo(userInfoRepository.save(userInfo));
        userAuthorityRepository.save(userAuthority);
//        userInfoRepository.save(
//                UserInfo.builder()
//                        .userId(talent.getId())
////                        .userAuthorities(Set.of(userAuthorityRepository.findById(Integer.toUnsignedLong(1))
////                                                                       .orElseThrow(() -> new ResponseStatusException(
////                                                                               HttpStatus.BAD_REQUEST,
////                                                                               "user didn't created"))))
//                        .userAuthorities(userAuthorityRepository.findByAuthority_Authority("ROLE_TALENT"))
//                        .login(user.login())
//                        .password(passwordEncoder.encode(user.password()))
//                        .build()
//        );
        return "YAY";
    }

    private String createScope(Authentication authentication) {
        return authentication.getAuthorities().stream()
                             .map(GrantedAuthority::getAuthority)
                             .collect(Collectors.joining(" "));
    }
}
