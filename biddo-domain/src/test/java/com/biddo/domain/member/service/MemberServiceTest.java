package com.biddo.domain.member.service;

import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.BanType;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.entity.MemberRole;
import com.biddo.domain.member.exception.MemberErrorCode;
import com.biddo.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Nested
    @DisplayName("회원 생성")
    class CreateMember {

        @Test
        @DisplayName("성공")
        void success() {
            given(memberRepository.existsByEmail("test@test.com")).willReturn(false);
            given(memberRepository.existsByNickname("tester")).willReturn(false);
            given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

            Member member = memberService.createMember("test@test.com", "encoded", "tester");

            assertThat(member.getEmail()).isEqualTo("test@test.com");
            assertThat(member.getNickname()).isEqualTo("tester");
            verify(memberRepository).save(any(Member.class));
        }

        @Test
        @DisplayName("이메일 중복 시 실패")
        void duplicateEmail() {
            given(memberRepository.existsByEmail("test@test.com")).willReturn(true);

            assertThatThrownBy(() -> memberService.createMember("test@test.com", "encoded", "tester"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.DUPLICATE_EMAIL);
        }

        @Test
        @DisplayName("닉네임 중복 시 실패")
        void duplicateNickname() {
            given(memberRepository.existsByEmail("test@test.com")).willReturn(false);
            given(memberRepository.existsByNickname("tester")).willReturn(true);

            assertThatThrownBy(() -> memberService.createMember("test@test.com", "encoded", "tester"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.DUPLICATE_NICKNAME);
        }
    }

    @Nested
    @DisplayName("프로필 수정")
    class UpdateProfile {

        @Test
        @DisplayName("닉네임 변경 성공")
        void updateNicknameSuccess() {
            Member member = Member.builder().email("test@test.com").password("encoded").nickname("oldNick").build();
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(memberRepository.existsByNickname("newNick")).willReturn(false);

            Member updated = memberService.updateProfile(1L, "newNick", null, null);

            assertThat(updated.getNickname()).isEqualTo("newNick");
        }

        @Test
        @DisplayName("닉네임 중복 시 실패")
        void updateNicknameDuplicate() {
            Member member = Member.builder().email("test@test.com").password("encoded").nickname("oldNick").build();
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(memberRepository.existsByNickname("taken")).willReturn(true);

            assertThatThrownBy(() -> memberService.updateProfile(1L, "taken", null, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.DUPLICATE_NICKNAME);
        }
    }

    @Nested
    @DisplayName("계정 제재")
    class Ban {

        @Test
        @DisplayName("제재 성공 - WARNING")
        void banWarning() {
            Member member = Member.builder().email("bad@test.com").password("encoded").nickname("badUser").build();
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));

            Member result = memberService.ban(1L, BanType.WARNING, "규칙 위반", null);

            assertThat(result.getBanType()).isEqualTo(BanType.WARNING);
            assertThat(result.getBanReason()).isEqualTo("규칙 위반");
        }

        @Test
        @DisplayName("제재 성공 - SUSPEND")
        void banSuspend() {
            Member member = Member.builder().email("bad@test.com").password("encoded").nickname("badUser").build();
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));

            Member result = memberService.ban(1L, BanType.SUSPEND, "반복 위반", 7);

            assertThat(result.getBanType()).isEqualTo(BanType.SUSPEND);
            assertThat(result.getBanEndDate()).isNotNull();
        }

        @Test
        @DisplayName("제재 성공 - BAN")
        void banPermanent() {
            Member member = Member.builder().email("bad@test.com").password("encoded").nickname("badUser").build();
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));

            Member result = memberService.ban(1L, BanType.BAN, "영구 정지", null);

            assertThat(result.getBanType()).isEqualTo(BanType.BAN);
            assertThat(result.isBanned()).isTrue();
        }

        @Test
        @DisplayName("제재 실패 - ADMIN 계정")
        void banAdmin() {
            Member admin = Member.builder().email("admin@test.com").password("encoded").nickname("admin").build();
            setField(admin, "role", MemberRole.ADMIN);
            given(memberRepository.findById(1L)).willReturn(Optional.of(admin));

            assertThatThrownBy(() -> memberService.ban(1L, BanType.BAN, "테스트", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(MemberErrorCode.CANNOT_BAN_ADMIN);
        }

        @Test
        @DisplayName("제재 해제 성공")
        void unban() {
            Member member = Member.builder().email("bad@test.com").password("encoded").nickname("badUser").build();
            member.ban(BanType.SUSPEND, "위반", null);
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));

            Member result = memberService.unban(1L);

            assertThat(result.getBanType()).isNull();
            assertThat(result.isBanned()).isFalse();
        }
    }

    private void setField(Object entity, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}