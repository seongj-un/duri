package com.duri.expense.domain

import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

/**
 * 월별 뷰가 필요로 하는 조회·집계를 모아 둔다.
 *
 * 부담 몫은 DB 에서 계산한다. Expense.payerShare 와 같은 식(정수 나눗셈, 내림)을 쓰므로
 * 건별로 계산해 합친 값과 결과가 일치한다. 합계를 먼저 내고 비율을 곱하면
 * 반올림 위치가 달라져 1원씩 어긋난다.
 */
@Repository
class ExpenseQueryRepository(
    private val queryFactory: JPAQueryFactory,
) {

    private val expense = QExpense.expense

    /** amount * rate / 100 (정수 나눗셈) */
    private val payerShare: NumberExpression<Long> = Expressions.numberTemplate(
        Long::class.java,
        "({0} * {1} / 100)",
        expense.amount,
        expense.payerBurdenRate,
    )

    /** amount - payerShare. 나누어떨어지지 않고 남는 1원은 상대 몫으로 간다. */
    private val partnerShare: NumberExpression<Long> = Expressions.numberTemplate(
        Long::class.java,
        "({0} - ({0} * {1} / 100))",
        expense.amount,
        expense.payerBurdenRate,
    )

    fun findPage(condition: ExpenseSearchCondition, pageable: Pageable): Page<Expense> {
        val where = condition.toPredicate()

        val content = queryFactory
            .selectFrom(expense)
            .where(where)
            .orderBy(expense.spentAt.desc(), expense.id.desc())
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        // count 는 정렬·페치 없이 따로 센다. 목록 쿼리에 붙이면 불필요한 조인이 남는다.
        val countQuery = queryFactory
            .select(expense.count())
            .from(expense)
            .where(where)

        return PageImpl(content, pageable, countQuery.fetchOne() ?: 0L)
    }

    /** 필터 전체의 합계. 페이지 단위가 아니라 목록 상단에 띄우는 값이다. */
    fun sumAmount(condition: ExpenseSearchCondition): Long =
        queryFactory
            .select(expense.amount.sum())
            .from(expense)
            .where(condition.toPredicate())
            .fetchOne() ?: 0L

    fun aggregateByPayer(condition: ExpenseSearchCondition): List<PayerAggregate> =
        queryFactory
            .select(
                Projections.constructor(
                    PayerAggregate::class.java,
                    expense.payerId,
                    expense.amount.sum().coalesce(0L),
                    payerShare.sum().coalesce(0L),
                    partnerShare.sum().coalesce(0L),
                    expense.count(),
                ),
            )
            .from(expense)
            .where(condition.toPredicate())
            .groupBy(expense.payerId)
            .fetch()

    /**
     * 월 x 카테고리 교차 집계.
     *
     * to_char 는 인덱스 식으로는 못 쓰지만(IMMUTABLE 이 아님) 조회에서는 문제없다.
     * 월을 문자열 YYYYMM 으로 뽑아 두면 정렬이 곧 시간순이라 그대로 축으로 쓸 수 있다.
     */
    fun aggregateByMonthAndCategory(condition: ExpenseSearchCondition): List<MonthCategoryAggregate> {
        val period = Expressions.stringTemplate("to_char({0}, 'YYYYMM')", expense.spentAt)

        return queryFactory
            .select(
                Projections.constructor(
                    MonthCategoryAggregate::class.java,
                    period,
                    expense.category,
                    expense.amount.sum().coalesce(0L),
                    expense.count(),
                ),
            )
            .from(expense)
            .where(condition.toPredicate())
            .groupBy(period, expense.category)
            .orderBy(period.asc())
            .fetch()
    }

    fun aggregateByCategory(condition: ExpenseSearchCondition): List<CategoryAggregate> =
        queryFactory
            .select(
                Projections.constructor(
                    CategoryAggregate::class.java,
                    expense.category,
                    expense.amount.sum().coalesce(0L),
                    expense.count(),
                ),
            )
            .from(expense)
            .where(condition.toPredicate())
            .groupBy(expense.category)
            .orderBy(expense.amount.sum().desc())
            .fetch()

    private fun ExpenseSearchCondition.toPredicate() = BooleanBuilder()
        .and(expense.couple.id.eq(coupleId))
        .and(expense.deletedAt.isNull)
        .and(expense.spentAt.between(from, to))
        .apply {
            category?.let { and(expense.category.eq(it)) }
            payerId?.let { and(expense.payerId.eq(it)) }
        }
}
