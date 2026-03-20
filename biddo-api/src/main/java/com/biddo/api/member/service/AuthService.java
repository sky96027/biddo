package com.biddo.api.member.service;

import com.biddo.api.common.security.JwtTokenProvider;
import com.biddo.api.member.dto.response.TokenResponse;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.exception.MemberErrorCode;
import com.biddo.domain.member.service.MemberService;
import com.biddo.infra.redis.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberService memberService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    public Member signup(String email, String password, String nickname) {
        String encodedPassword = passwordEncoder.encode(password);
        return memberService.createMember(email, encodedPassword, nickname);
    }

    public TokenResponse login(String email, String password) {
        Member member = memberService.findByEmail(email);

        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new BusinessException(MemberErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(member);
    }

    public void logout(Long memberId) {
        refreshTokenRepository.deleteByMemberId(memberId);
    }

    public TokenResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(MemberErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long memberId = jwtTokenProvider.getMemberId(refreshToken);

        String storedToken = refreshTokenRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.INVALID_REFRESH_TOKEN));

        if (!storedToken.equals(refreshToken)) {
            throw new BusinessException(MemberErrorCode.INVALID_REFRESH_TOKEN);
        }

        Member member = memberService.findById(memberId);
        return issueTokens(member);
    }

    public void changePassword(Long memberId, String currentPassword, String newPassword) {
        Member member = memberService.findById(memberId);

        if (!passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new BusinessException(MemberErrorCode.INVALID_PASSWORD);
        }

        String encodedPassword = passwordEncoder.encode(newPassword);
        memberService.changePassword(memberId, encodedPassword);
        refreshTokenRepository.deleteByMemberId(memberId);
    }

    private TokenResponse issueTokens(Member member) {
        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId());
        refreshTokenRepository.save(member.getId(), refreshToken);
        return new TokenResponse(accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpiration());
    }
}