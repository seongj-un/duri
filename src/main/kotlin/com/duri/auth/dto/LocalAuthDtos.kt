package com.duri.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val email: String,

    // Size 만으로는 공백 여덟 칸이 통과한다.
    @field:NotBlank
    // 상한을 두는 이유: bcrypt 는 72바이트를 넘는 입력을 조용히 잘라낸다.
    @field:Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
    val password: String,

    @field:NotBlank
    @field:Size(max = 50)
    val nickname: String,
)

data class LoginRequest(
    @field:NotBlank
    val email: String,

    @field:NotBlank
    val password: String,
)
