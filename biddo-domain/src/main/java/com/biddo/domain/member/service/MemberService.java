package com.biddo.domain.member.service;

import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.exception.MemberErrorCode;
import com.biddo.domain.member.exception.MemberNotFoundException;
import com.biddo.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private void validateDuplicate(String email, String nickname) {
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(MemberErrorCode.DUPLICATE_EMAIL);
        }
        if (memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(MemberErrorCode.DUPLICATE_NICKNAME);
        }
    }
}