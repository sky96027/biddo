package com.biddo.api.member.service;

import com.biddo.api.common.security.JwtTokenProvider;
import com.biddo.api.member.dto.response.TokenResponse;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.exception.MemberErrorCode;
import com.biddo.domain.member.service.MemberService;
import com.biddo.infra.redis.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private MemberService memberService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private Member createMember() {
        Member member = Member.builder()
                .email("test@test.com")
                .password("encodedPassword")
                .nickname("tester")
                .build();
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    @Nested
    @DisplayName("회원가입")
    class Signup {

        @Test
        @DisplayName("성공 — 비밀번호 인코딩 후 MemberService에 위임")
        void signup_validInput_success() {
            given(passwordEncoder.encode("rawPassword")).willReturn("encodedPassword");
            Member member = createMember();
            given(memberService.createMember("test@test.com", "encodedPassword", "tester")).willReturn(member);

            Member result = authService.signup("test@test.com", "rawPassword", "tester");

            assertThat(result.getEmail()).isEqualTo("test@test.com");
            verify(passwordEncoder).encode("rawPassword");
        }
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("성공 — 토큰 발급 및 Redis 저장")
        void login_validCredentials_returnsToken() {
            Member member = createMember();
            given(memberService.findByEmail("test@test.com")).willReturn(member);
            given(passwordEncoder.matches("rawPassword", "encodedPassword")).willReturn(true);
            given(jwtTokenProvider.createAccessToken(1L, "USER")).willReturn("accessToken");
            given(jwtTokenProvider.createRefreshToken(1L)).willReturn("refreshToken");
            given(jwtTokenProvider.getAccessTokenExpiration()).willReturn(1800000L);

            TokenResponse response = authService.login("test@test.com", "rawPassword");

            assertThat(response.getAccessToken()).isEqualTo("accessToken");
            assertThat(response.getRefreshToken()).isEqualTo("refreshToken");
            verify(refreshTokenRepository).save(1L, "refreshToken");
        }

        @Test
        @DisplayName("비밀번호 불일치 시 실패")
        void login_wrongPassword_throwsException() {
            Member member = createMember();
            given(memberService.findByEmail("test@test.com")).willReturn(member);
            given(passwordEncoder.matches("wrong", "encodedPassword")).willReturn(false);

            assertThatThrownBy(() -> authService.login("test@test.com", "wrong"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.INVALID_CREDENTIALS);
        }
    }

    @Nested
    @DisplayName("토큰 재발급")
    class Refresh {

        @Test
        @DisplayName("성공")
        void refresh_validToken_returnsNewToken() {
            given(jwtTokenProvider.validateToken("validRefresh")).willReturn(true);
            given(jwtTokenProvider.getMemberId("validRefresh")).willReturn(1L);
            given(refreshTokenRepository.findByMemberId(1L)).willReturn(Optional.of("validRefresh"));
            Member member = createMember();
            given(memberService.findById(1L)).willReturn(member);
            given(jwtTokenProvider.createAccessToken(1L, "USER")).willReturn("newAccess");
            given(jwtTokenProvider.createRefreshToken(1L)).willReturn("newRefresh");
            given(jwtTokenProvider.getAccessTokenExpiration()).willReturn(1800000L);

            TokenResponse response = authService.refresh("validRefresh");

            assertThat(response.getAccessToken()).isEqualTo("newAccess");
            verify(refreshTokenRepository).save(1L, "newRefresh");
        }

        @Test
        @DisplayName("유효하지 않은 토큰 시 실패")
        void refresh_invalidToken_throwsException() {
            given(jwtTokenProvider.validateToken("invalid")).willReturn(false);

            assertThatThrownBy(() -> authService.refresh("invalid"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.INVALID_REFRESH_TOKEN);
        }

        @Test
        @DisplayName("Redis 저장 토큰과 불일치 시 실패")
        void refresh_tokenMismatch_throwsException() {
            given(jwtTokenProvider.validateToken("stolenToken")).willReturn(true);
            given(jwtTokenProvider.getMemberId("stolenToken")).willReturn(1L);
            given(refreshTokenRepository.findByMemberId(1L)).willReturn(Optional.of("realToken"));

            assertThatThrownBy(() -> authService.refresh("stolenToken"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    @Nested
    @DisplayName("비밀번호 변경")
    class ChangePassword {

        @Test
        @DisplayName("성공 — 비밀번호 변경 후 Refresh Token 삭제")
        void changePassword_validInput_success() {
            Member member = createMember();
            given(memberService.findById(1L)).willReturn(member);
            given(passwordEncoder.matches("current", "encodedPassword")).willReturn(true);
            given(passwordEncoder.encode("newPassword")).willReturn("newEncoded");

            authService.changePassword(1L, "current", "newPassword");

            verify(memberService).changePassword(1L, "newEncoded");
            verify(refreshTokenRepository).deleteByMemberId(1L);
        }

        @Test
        @DisplayName("현재 비밀번호 불일치 시 실패")
        void changePassword_wrongCurrent_throwsException() {
            Member member = createMember();
            given(memberService.findById(1L)).willReturn(member);
            given(passwordEncoder.matches("wrong", "encodedPassword")).willReturn(false);

            assertThatThrownBy(() -> authService.changePassword(1L, "wrong", "newPassword"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.INVALID_PASSWORD);
        }
    }
}