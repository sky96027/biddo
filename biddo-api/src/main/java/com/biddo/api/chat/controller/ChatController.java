package com.biddo.api.chat.controller;

import com.biddo.api.chat.dto.request.ChatMessageRequest;
import com.biddo.api.chat.dto.response.ChatMessageResponse;
import com.biddo.api.chat.dto.response.ChatRoomResponse;
import com.biddo.api.common.response.ApiResponse;
import com.biddo.api.common.response.CursorResponse;
import com.biddo.api.common.security.CustomUserDetails;
import com.biddo.domain.chat.entity.ChatMessage;
import com.biddo.domain.chat.entity.MessageType;
import com.biddo.domain.chat.service.ChatService;
import com.biddo.domain.member.entity.Member;
import com.biddo.domain.member.service.MemberService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "채팅")
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final int DEFAULT_SIZE = 50;

    private final ChatService chatService;
    private final MemberService memberService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/rooms")
    public ApiResponse<List<ChatRoomResponse>> getMyRooms(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long memberId = userDetails.getMemberId();
        List<ChatRoomResponse> responses = chatService.findMyRoomsWithSummary(memberId).stream()
                .map(s -> ChatRoomResponse.of(s.room(), memberId, s.latestMessage(), s.unreadCount()))
                .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ApiResponse<CursorResponse<ChatMessageResponse>> getMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long roomId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "50") int size) {
        int fetchSize = Math.min(size, DEFAULT_SIZE);
        List<ChatMessage> messages = chatService.findMessages(
                roomId, userDetails.getMemberId(), cursor, fetchSize + 1);

        boolean hasNext = messages.size() > fetchSize;
        List<ChatMessage> content = hasNext ? messages.subList(0, fetchSize) : messages;
        List<ChatMessageResponse> responses = content.stream()
                .map(ChatMessageResponse::from).toList();

        String nextCursor = hasNext
                ? String.valueOf(content.get(content.size() - 1).getId())
                : null;

        return ApiResponse.success(CursorResponse.of(responses, nextCursor, hasNext));
    }

    @PostMapping("/rooms/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long roomId,
            @Valid @RequestBody ChatMessageRequest request) {
        Member sender = memberService.findById(userDetails.getMemberId());
        ChatMessage message = chatService.sendMessage(
                roomId, sender.getId(), request.getContent(),
                MessageType.TEXT, request.getImageUrl(), sender);

        ChatMessageResponse response = ChatMessageResponse.from(message);
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, response);
        return ApiResponse.success(response);
    }
}