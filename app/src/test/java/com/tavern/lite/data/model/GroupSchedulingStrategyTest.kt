package com.tavern.lite.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupSchedulingStrategyTest {

    @Test
    fun `fromKey returns NATURAL for natural key`() {
        assertEquals(GroupSchedulingStrategy.NATURAL, GroupSchedulingStrategy.fromKey("natural"))
    }

    @Test
    fun `fromKey returns LIST_ORDER for list_order key`() {
        assertEquals(GroupSchedulingStrategy.LIST_ORDER, GroupSchedulingStrategy.fromKey("list_order"))
    }

    @Test
    fun `fromKey returns ROUND_ROBIN for round_robin key`() {
        assertEquals(GroupSchedulingStrategy.ROUND_ROBIN, GroupSchedulingStrategy.fromKey("round_robin"))
    }

    @Test
    fun `fromKey defaults to NATURAL for unknown key`() {
        assertEquals(GroupSchedulingStrategy.NATURAL, GroupSchedulingStrategy.fromKey("unknown"))
    }

    @Test
    fun `fromKey defaults to NATURAL for empty key`() {
        assertEquals(GroupSchedulingStrategy.NATURAL, GroupSchedulingStrategy.fromKey(""))
    }

    @Test
    fun `key property returns correct string`() {
        assertEquals("natural", GroupSchedulingStrategy.NATURAL.key)
        assertEquals("list_order", GroupSchedulingStrategy.LIST_ORDER.key)
        assertEquals("round_robin", GroupSchedulingStrategy.ROUND_ROBIN.key)
    }
}
