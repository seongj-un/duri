package com.duri.user

import com.duri.common.error.BusinessException
import com.duri.common.error.ErrorCode
import com.duri.support.Fixtures
import com.duri.support.IntegrationTestBase
import com.duri.user.application.AccountService
import com.duri.user.domain.AccountRepository
import com.duri.user.domain.Bank
import com.duri.user.domain.UserRepository
import com.duri.user.dto.AccountRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

@DisplayName("정산 계좌")
class AccountServiceTest(
    private val accountService: AccountService,
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository,
    private val jdbcTemplate: JdbcTemplate,
) : IntegrationTestBase() {

    private fun newUser() = userRepository.save(Fixtures.user()).requiredId

    @Test
    fun `계좌를 등록하면 은행 코드와 이름이 함께 저장된다`() {
        val userId = newUser()

        val saved = accountService.upsert(userId, AccountRequest(Bank.SHINHAN, "110-123-456789", "박성준"))

        assertThat(saved.bank).isEqualTo(Bank.SHINHAN)
        assertThat(saved.bankName).isEqualTo("신한")
        assertThat(saved.holderName).isEqualTo("박성준")
        // 하이픈은 저장 전에 걷어낸다. 복사해 붙여넣을 때 형식이 일정해야 하기 때문이다.
        assertThat(saved.accountNo).isEqualTo("110123456789")
    }

    @Test
    fun `다시 등록하면 새로 만들지 않고 갈아끼운다`() {
        val userId = newUser()
        accountService.upsert(userId, AccountRequest(Bank.KB, "12345678901234", "박성준"))

        val updated = accountService.upsert(userId, AccountRequest(Bank.TOSSBANK, "100012345678", "박성준"))

        assertThat(updated.bank).isEqualTo(Bank.TOSSBANK)
        assertThat(accountRepository.findAll()).hasSize(1)
    }

    @Test
    fun `계좌번호는 DB 에 평문으로 남지 않는다`() {
        val userId = newUser()
        accountService.upsert(userId, AccountRequest(Bank.KB, "12345678901234", "박성준"))

        // JPA 를 거치지 않고 컬럼을 그대로 읽어 실제 저장된 값을 본다
        val stored = jdbcTemplate.queryForObject(
            "select account_no from accounts where user_id = ?",
            String::class.java,
            userId,
        )!!

        assertThat(stored).doesNotContain("12345678901234")
        // 그러면서도 애플리케이션에서는 평문으로 읽힌다
        assertThat(accountService.get(userId).accountNo).isEqualTo("12345678901234")
    }

    @Test
    fun `등록한 적이 없으면 404 로 알린다`() {
        val userId = newUser()

        assertThatThrownBy { accountService.get(userId) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_NOT_REGISTERED)
    }

    @Test
    fun `삭제하면 다시 조회되지 않고 두 번 삭제해도 조용하다`() {
        val userId = newUser()
        accountService.upsert(userId, AccountRequest(Bank.NH, "3021234567890", "박성준"))

        accountService.delete(userId)
        accountService.delete(userId)

        assertThat(accountRepository.findAll()).isEmpty()
    }

    @Test
    fun `마스킹은 뒤 네 자리만 남긴다`() {
        val userId = newUser()
        accountService.upsert(userId, AccountRequest(Bank.KB, "12345678901234", "박성준"))

        val account = accountRepository.findByUserId(userId)!!

        assertThat(account.maskedAccountNo()).isEqualTo("**********1234")
    }
}
