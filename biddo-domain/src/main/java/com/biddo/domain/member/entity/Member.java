package com.biddo.domain.member.entity;

import com.biddo.domain.common.entity.BaseTimeEntity;
import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.exception.MemberErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(length = 500)
    private String profileImageUrl;

    @Column(length = 500)
    private String introduction;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal trustScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BanType banType;

    @Column(length = 500)
    private String banReason;

    private LocalDateTime banEndDate;

    @Builder
    public Member(String email, String password, String nickname) {
        validateNickname(nickname);
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.trustScore = BigDecimal.ZERO;
        this.role = MemberRole.USER;
    }

    public void updateProfile(String nickname, String introduction, String profileImageUrl) {
        if (nickname != null) {
            validateNickname(nickname);
            this.nickname = nickname;
        }
        if (introduction != null) {
            validateIntroduction(introduction);
            this.introduction = introduction;
        }
        if (profileImageUrl != null) this.profileImageUrl = profileImageUrl;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void ban(BanType banType, String reason, LocalDateTime endDate) {
        this.banType = banType;
        this.banReason = reason;
        this.banEndDate = endDate;
    }

    public void unban() {
        this.banType = null;
        this.banReason = null;
        this.banEndDate = null;
    }

    public boolean isBanned() {
        if (this.banType == null) return false;
        if (this.banType == BanType.BAN) return true;
        if (this.banType == BanType.SUSPEND && this.banEndDate != null) {
            return LocalDateTime.now().isBefore(this.banEndDate);
        }
        return false;
    }

    private void validateNickname(String nickname) {
        if (nickname.length() < 2 || nickname.length() > 50) {
            throw new BusinessException(MemberErrorCode.INVALID_NICKNAME_LENGTH);
        }
    }

    private void validateIntroduction(String introduction) {
        if (introduction.length() > 500) {
            throw new BusinessException(MemberErrorCode.INVALID_INTRODUCTION_LENGTH);
        }
    }
}