package com.biddo.domain.search.service;

import com.biddo.domain.common.exception.BusinessException;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.exception.MemberErrorCode;
import com.biddo.domain.member.service.MemberService;
import com.biddo.domain.search.entity.KeywordAlert;
import com.biddo.domain.search.exception.SearchErrorCode;
import com.biddo.domain.search.repository.KeywordAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class KeywordAlertServiceTest {

    @InjectMocks
    private KeywordAlertService keywordAlertService;

    @Mock
    private KeywordAlertRepository keywordAlertRepository;

    @Mock
    private MemberService memberService;

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.builder()
                .email("test@test.com")
                .password("encoded")
                .nickname("tester")
                .build();
        setId(member, 1L);
    }

    @Test
    @DisplayName("키워드 알림 생성 성공")
    void create_validInput_success() {
        given(keywordAlertRepository.existsByMemberIdAndKeyword(1L, "아이폰")).willReturn(false);
        given(memberService.findById(1L)).willReturn(member);
        given(keywordAlertRepository.save(any(KeywordAlert.class))).willAnswer(inv -> {
            KeywordAlert alert = inv.getArgument(0);
            setId(alert, 1L);
            return alert;
        });

        KeywordAlert result = keywordAlertService.create(1L, "아이폰", 9L, 500_000L);

        assertThat(result.getKeyword()).isEqualTo("아이폰");
        assertThat(result.getCategoryId()).isEqualTo(9L);
        assertThat(result.getMaxPrice()).isEqualTo(500_000L);
        assertThat(result.isActive()).isTrue();
        verify(keywordAlertRepository).save(any(KeywordAlert.class));
    }

    @Test
    @DisplayName("중복 키워드 알림 생성 시 예외 발생")
    void create_duplicateKeyword_throwsException() {
        given(keywordAlertRepository.existsByMemberIdAndKeyword(1L, "아이폰")).willReturn(true);

        assertThatThrownBy(() -> keywordAlertService.create(1L, "아이폰", 9L, 500_000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(SearchErrorCode.KEYWORD_ALERT_DUPLICATE));
    }

    @Test
    @DisplayName("존재하지 않는 회원으로 알림 생성 시 예외 발생")
    void create_memberNotFound_throwsException() {
        given(keywordAlertRepository.existsByMemberIdAndKeyword(1L, "아이폰")).willReturn(false);
        given(memberService.findById(1L)).willThrow(new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        assertThatThrownBy(() -> keywordAlertService.create(1L, "아이폰", 9L, 500_000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    @DisplayName("키워드 알림 수정 성공")
    void update_validInput_success() {
        KeywordAlert alert = createAlert(1L, "아이폰", 9L, 500_000L);
        given(keywordAlertRepository.findById(1L)).willReturn(Optional.of(alert));

        KeywordAlert result = keywordAlertService.update(1L, 1L, 10L, 800_000L);

        assertThat(result.getCategoryId()).isEqualTo(10L);
        assertThat(result.getMaxPrice()).isEqualTo(800_000L);
    }

    @Test
    @DisplayName("다른 회원의 알림 수정 시 예외 발생")
    void update_notOwner_throwsException() {
        KeywordAlert alert = createAlert(1L, "아이폰", 9L, 500_000L);
        given(keywordAlertRepository.findById(1L)).willReturn(Optional.of(alert));

        assertThatThrownBy(() -> keywordAlertService.update(1L, 999L, 10L, 800_000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(SearchErrorCode.NOT_KEYWORD_ALERT_OWNER));
    }

    @Test
    @DisplayName("활성 알림 토글 시 비활성화")
    void toggleActive_activeAlert_deactivatesAlert() {
        KeywordAlert alert = createAlert(1L, "아이폰", 9L, 500_000L);
        given(keywordAlertRepository.findById(1L)).willReturn(Optional.of(alert));

        keywordAlertService.toggleActive(1L, 1L);

        assertThat(alert.isActive()).isFalse();
    }

    @Test
    @DisplayName("비활성 알림 토글 시 활성화")
    void toggleActive_inactiveAlert_activatesAlert() {
        KeywordAlert alert = createAlert(1L, "아이폰", 9L, 500_000L);
        alert.deactivate();
        given(keywordAlertRepository.findById(1L)).willReturn(Optional.of(alert));

        keywordAlertService.toggleActive(1L, 1L);

        assertThat(alert.isActive()).isTrue();
    }

    @Test
    @DisplayName("키워드 알림 삭제 성공")
    void delete_validInput_success() {
        KeywordAlert alert = createAlert(1L, "아이폰", 9L, 500_000L);
        given(keywordAlertRepository.findById(1L)).willReturn(Optional.of(alert));

        keywordAlertService.delete(1L, 1L);

        verify(keywordAlertRepository).delete(alert);
    }

    @Test
    @DisplayName("존재하지 않는 알림 삭제 시 예외 발생")
    void delete_notFound_throwsException() {
        given(keywordAlertRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> keywordAlertService.delete(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(SearchErrorCode.KEYWORD_ALERT_NOT_FOUND));
    }

    @Test
    @DisplayName("다른 회원의 알림 삭제 시 예외 발생")
    void delete_notOwner_throwsException() {
        KeywordAlert alert = createAlert(1L, "아이폰", 9L, 500_000L);
        given(keywordAlertRepository.findById(1L)).willReturn(Optional.of(alert));

        assertThatThrownBy(() -> keywordAlertService.delete(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(SearchErrorCode.NOT_KEYWORD_ALERT_OWNER));
    }

    private KeywordAlert createAlert(Long id, String keyword, Long categoryId, Long maxPrice) {
        KeywordAlert alert = KeywordAlert.builder()
                .member(member)
                .keyword(keyword)
                .categoryId(categoryId)
                .maxPrice(maxPrice)
                .build();
        setId(alert, id);
        return alert;
    }

    private void setId(Object entity, Long id) {
        try {
            java.lang.reflect.Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
