# API 응답 규칙

## 반환 타입

`ApiResponse<T>`를 직접 반환할 것. `ResponseEntity`로 감싸지 말 것.

```java
// 올바름
public ApiResponse<AuctionResponse> getAuction(...) {
    return ApiResponse.success(response);
}

// 금지
public ResponseEntity<ApiResponse<AuctionResponse>> getAuction(...) { ... }
```

## HTTP 상태 코드

`@ResponseStatus`로 제어할 것. `ResponseEntity.status()`를 사용하지 말 것.

| 동작 | 상태 코드 | 처리 방식 |
|------|----------|-----------|
| 조회/수정 | 200 | 명시 불필요 (기본값) |
| 생성 | 201 | `@ResponseStatus(HttpStatus.CREATED)` |
| 삭제 | 204 | `@ResponseStatus(HttpStatus.NO_CONTENT)` |

## 성공 응답

```java
// 데이터 반환
return ApiResponse.success(data);

// 데이터 없음 (삭제 등)
return ApiResponse.success();
```

- `ApiResponse.success(null)` 사용 **금지**. 인자 없는 `ApiResponse.success()` 사용할 것.

## 204 No Content

`@ResponseStatus(HttpStatus.NO_CONTENT)` + `ApiResponse<Void>` 반환.

```java
@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public ApiResponse<Void> delete(...) {
    service.delete(id);
    return ApiResponse.success();
}
```

## 페이지네이션

`CursorResponse<T>` 사용할 것. Cursor 기반 (No-Offset).

```java
return ApiResponse.success(CursorResponse.of(responses, nextCursor, hasNext));
```

- `totalElements` **금지** — COUNT 쿼리 제거 목적.

## 에러 응답

`GlobalExceptionHandler`에서 통일 처리. Controller에서 try-catch **금지**.

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUCTION_001",
    "message": "경매를 찾을 수 없습니다."
  }
}
```

## 예외

- SSE 엔드포인트는 `SseEmitter` 직접 반환 허용.