package com.duri.notification

import com.duri.couple.application.CoupleService
import com.duri.couple.dto.CoupleUpdateRequest
import com.duri.expense.application.ExpenseService
import com.duri.expense.domain.ExpenseCategory
import com.duri.expense.dto.ExpenseCreateRequest
import com.duri.notification.application.NotificationService
import com.duri.notification.application.SettlementReminderScheduler
import com.duri.notification.domain.NotificationRepository
import com.duri.notification.domain.NotificationType
import com.duri.settlement.application.SettlementService
import com.duri.support.ActiveCouple
import com.duri.support.CoupleFixture
import com.duri.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("정산일 리마인드 알림")
class SettlementReminderTest(
    private val scheduler: SettlementReminderScheduler,
    private val notificationService: NotificationService,
    private val notificationRepository: NotificationRepository,
    private val expenseService: ExpenseService,
    private val settlementService: SettlementService,
    private val coupleService: CoupleService,
    private val coupleFixture: CoupleFixture,
) : IntegrationTestBase() {

    private lateinit var couple: ActiveCouple

    /** 기준일 1일에 지난달(2025-12)을 정산하라고 알린다. */
    private val remindDay = LocalDate.of(2026, 1, 1)
    private val december = YearMonth.of(2025, 12)

    @BeforeEach
    fun setUp() {
        couple = coupleFixture.active(ownerNickname = "성준", partnerNickname = "지현")
    }

    private fun spendInDecember(payerId: Long, amount: Long) = expenseService.create(
        couple.ownerId,
        ExpenseCreateRequest(payerId, amount, ExpenseCategory.GROCERY, 50, LocalDate.of(2025, 12, 15), null),
    )

    @Test
    fun `기준일이 되면 두 사람 모두에게 알림이 간다`() {
        spendInDecember(couple.ownerId, 300_000)

        val report = scheduler.remindOn(remindDay)

        assertThat(report.sent).isEqualTo(1)
        val notifications = notificationRepository.findAll()
        assertThat(notifications).hasSize(2)
        assertThat(notifications.map { it.userId })
            .containsExactlyInAnyOrder(couple.ownerId, couple.partnerId)
        assertThat(notifications).allMatch { it.type == NotificationType.SETTLEMENT_REMINDER }
        assertThat(notifications).allMatch { it.period == "202512" }
    }

    @Test
    fun `알림 문구에 누가 누구에게 얼마를 보낼지 담긴다`() {
        spendInDecember(couple.ownerId, 300_000)

        scheduler.remindOn(remindDay)

        val notification = notificationRepository.findAll().first()
        assertThat(notification.title).isEqualTo("12월 정산할 시간이에요")
        assertThat(notification.body).isEqualTo("지현님이 성준님에게 150,000원 보내면 정산 완료예요.")
    }

    @Test
    fun `주고받을 금액이 없으면 그렇게 알린다`() {
        spendInDecember(couple.ownerId, 100_000)
        spendInDecember(couple.partnerId, 100_000)

        scheduler.remindOn(remindDay)

        assertThat(notificationRepository.findAll().first().body)
            .isEqualTo("이번 달은 주고받을 금액이 없어요.")
    }

    @Test
    fun `이미 확정한 달은 알리지 않는다`() {
        spendInDecember(couple.ownerId, 300_000)
        settlementService.confirm(couple.ownerId, december)

        val report = scheduler.remindOn(remindDay)

        assertThat(report.sent).isZero()
        assertThat(report.skipped).isEqualTo(1)
        assertThat(notificationRepository.findAll()).isEmpty()
    }

    @Test
    fun `여러 번 돌려도 알림은 한 번만 간다`() {
        spendInDecember(couple.ownerId, 300_000)

        scheduler.remindOn(remindDay)
        val second = scheduler.remindOn(remindDay)

        assertThat(second.sent).isZero()
        assertThat(notificationRepository.findAll()).hasSize(2)
    }

    @Test
    fun `기준일이 아닌 날에는 훑을 대상이 없다`() {
        spendInDecember(couple.ownerId, 300_000)

        val report = scheduler.remindOn(LocalDate.of(2026, 1, 2))

        assertThat(report.scanned).isZero()
        assertThat(notificationRepository.findAll()).isEmpty()
    }

    @Test
    fun `기준일을 바꾸면 그 날에 알림이 간다`() {
        coupleService.update(couple.ownerId, CoupleUpdateRequest(settlementDay = 25))
        spendInDecember(couple.ownerId, 300_000)

        assertThat(scheduler.remindOn(LocalDate.of(2026, 1, 1)).scanned).isZero()
        assertThat(scheduler.remindOn(LocalDate.of(2026, 1, 25)).sent).isEqualTo(1)
    }

    @Test
    fun `알림함은 최신순으로 읽고 안 읽은 개수를 함께 준다`() {
        spendInDecember(couple.ownerId, 300_000)
        scheduler.remindOn(remindDay)

        val page = notificationService.list(couple.ownerId, PageRequest.of(0, 20))

        assertThat(page.content).hasSize(1)
        assertThat(page.unreadCount).isEqualTo(1)
        assertThat(page.content.first().read).isFalse()
        assertThat(page.content.first().period).isEqualTo("2025-12")
    }

    @Test
    fun `읽음 처리하면 안 읽은 개수가 줄어든다`() {
        spendInDecember(couple.ownerId, 300_000)
        scheduler.remindOn(remindDay)
        val notificationId = notificationService
            .list(couple.ownerId, PageRequest.of(0, 20)).content.first().notificationId

        notificationService.markRead(couple.ownerId, notificationId)

        assertThat(notificationService.list(couple.ownerId, PageRequest.of(0, 20)).unreadCount).isZero()
    }

    @Test
    fun `남의 알림은 읽음 처리할 수 없다`() {
        spendInDecember(couple.ownerId, 300_000)
        scheduler.remindOn(remindDay)
        val other = coupleFixture.active("A", "B")
        val notificationId = notificationService
            .list(couple.ownerId, PageRequest.of(0, 20)).content.first().notificationId

        org.assertj.core.api.Assertions
            .assertThatThrownBy { notificationService.markRead(other.ownerId, notificationId) }
            .isInstanceOf(com.duri.common.error.BusinessException::class.java)
    }
}
