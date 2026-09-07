package com.duri.common.web

/** 인증된 사용자의 id 를 컨트롤러 파라미터로 바로 받는다. */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentUserId
