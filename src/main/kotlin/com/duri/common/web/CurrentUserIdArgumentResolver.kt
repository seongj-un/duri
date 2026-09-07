package com.duri.common.web

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class CurrentUserIdArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentUserId::class.java) &&
            parameter.parameterType in SUPPORTED_TYPES

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Long {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        val jwt = principal as? Jwt ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        return jwt.subject?.toLongOrNull() ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
    }

    private companion object {
        /** 코틀린의 Long 은 원시형 long 으로, Long? 은 java.lang.Long 으로 컴파일된다. */
        val SUPPORTED_TYPES = setOf<Class<*>>(Long::class.java, Long::class.javaObjectType)
    }
}
