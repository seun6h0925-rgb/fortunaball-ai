package com.fortunaball.common.guest;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 컨트롤러 메서드에 "GuestToken 파라미터"만 적어 두면, 이 클래스가 X-Guest-Token 헤더를 읽어 검사한 뒤 넣어 준다.
 *   예) public ApiResponse<...> issue(GuestToken guest, ...) { ... guest.value() ... }
 * 헤더가 없거나 형식이 틀리면 컨트롤러가 실행되기 전에 COMMON-001 오류로 끝난다.
 * (등록은 GuestWebConfig 에서 한다)
 */
public class GuestTokenArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return GuestToken.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return GuestToken.of(webRequest.getHeader(GuestToken.HEADER_NAME));
    }
}
