package com.biddo.domain.member.service;

import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.BanType;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.entity.MemberRole;
import com.biddo.domain.member.exception.MemberErrorCode;
import com.biddo.domain.member.exception.MemberNotFoundException;
import com.biddo.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional
    public Member createMember(String email, String encodedPassword, String nickname) {
        validateDuplicate(email, nickname);

        Member member = Member.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .build();

        return memberRepository.save(member);
    }

    public Member findById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(MemberNotFoundException::new);
    }

    public Member findByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(MemberNotFoundException::new);
    }

    @Transactional
    public Member updateProfile(Long memberId, String nickname, String introduction, String profileImageUrl) {
        Member member = findById(memberId);

        if (nickname != null && !nickname.equals(member.getNickname())) {
            if (memberRepository.existsByNickname(nickname)) {
                throw new BusinessException(MemberErrorCode.DUPLICATE_NICKNAME);
            }
        }

        member.updateProfile(nickname, introduction, profileImageUrl);
        return member;
    }

    @Transactional
    public void changePassword(Long memberId, String encodedPassword) {
        Member member = findById(memberId);
        member.changePassword(encodedPassword);
    }

    public void validatePassword(String rawPassword) {
        if (rawPassword.length() < 8 || rawPassword.length() > 20) {
            throw new BusinessException(MemberErrorCode.INVALID_PASSWORD_LENGTH);
        }
    }

    @Transactional
    public Member ban(Long memberId, BanType banType, String reason, Integer durationDays) {
        Member member = findById(memberId);
        if (member.getRole() == MemberRole.ADMIN) {
            throw new BusinessException(MemberErrorCode.CANNOT_BAN_ADMIN);
        }

        LocalDateTime endDate = null;
        if (banType == BanType.SUSPEND && durationDays != null) {
            endDate = LocalDateTime.now().plusDays(durationDays);
        }

        member.ban(banType, reason, endDate);
        return member;
    }

    @Transactional
    public Member unban(Long memberId) {
        Member member = findById(memberId);
        member.unban();
        return member;
    }

    private void validateDuplicate(String email, String nickname) {
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(MemberErrorCode.DUPLICATE_EMAIL);
        }
        if (memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(MemberErrorCode.DUPLICATE_NICKNAME);
        }
    }
}