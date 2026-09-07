package com.duri.common.entity

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.proxy.HibernateProxy

@MappedSuperclass
abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    var id: Long? = null
        protected set

    /** 영속화 이후에만 쓴다. 저장 전 호출은 프로그래밍 오류다. */
    val requiredId: Long
        get() = id ?: error("${this::class.simpleName} 이(가) 아직 저장되지 않아 id 가 없습니다.")

    /** 프록시와 실체를 같은 것으로 보기 위해 Hibernate 프록시를 벗겨 비교한다. */
    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (effectiveClass(this) != effectiveClass(other)) return false
        val thisId = id ?: return false
        return thisId == (other as BaseEntity).id
    }

    /** id 는 저장 시점에 바뀌므로 해시에 넣지 않는다. */
    final override fun hashCode(): Int = effectiveClass(this).hashCode()

    private fun effectiveClass(target: Any): Class<*> =
        if (target is HibernateProxy) target.hibernateLazyInitializer.persistentClass else target.javaClass
}
