package com.biddo.api.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        String secret = Base64.getEncoder().encodeToString(
                "test-secret-key-for-jwt-token-generation-must-be-long-enough-256bit".getBytes());
        ReflectionTestUtils.setField(jwtTokenProvider, "secret", secret);
        ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenExpiration", 1800000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshTokenExpiration", 1209600000L);
        jwtTokenProvider.init();
    }

    @Test
    @DisplayName("Access Token 생성 및 파싱 성공")
    void createAccessToken_validInput_parsesCorrectly() {
        String token = jwtTokenProvider.createAccessToken(1L, "USER");

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getMemberId(token)).isEqualTo(1L);
        assertThat(jwtTokenProvider.getRole(token)).isEqualTo("USER");
    }

    @Test
    @DisplayName("Refresh Token 생성 — role 없음")
    void createRefreshToken_validInput_roleIsNull() {
        String token = jwtTokenProvider.createRefreshToken(1L);

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getMemberId(token)).isEqualTo(1L);
        assertThat(jwtTokenProvider.getRole(token)).isNull();
    }

    @Test
    @DisplayName("잘못된 토큰 검증 실패")
    void validateToken_invalidToken_returnsFalse() {
        assertThat(jwtTokenProvider.validateToken("invalid.token.here")).isFalse();
    }

    @Test
    @DisplayName("만료된 토큰 검증 실패")
    void validateToken_expiredToken_returnsFalse() {
        ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenExpiration", -1000L);
        jwtTokenProvider.init();

        String token = jwtTokenProvider.createAccessToken(1L, "USER");

        assertThat(jwtTokenProvider.validateToken(token)).isFalse();
    }
}