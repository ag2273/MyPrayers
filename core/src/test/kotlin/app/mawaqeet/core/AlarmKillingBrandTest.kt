package app.mawaqeet.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlarmKillingBrandTest {
    @Test
    fun `alarm-killing brands are recognised whatever the case, sub-brands included`() {
        assertEquals(AlarmKillingBrand.SAMSUNG, AlarmKillingBrand.of("samsung"))
        assertEquals(AlarmKillingBrand.XIAOMI, AlarmKillingBrand.of("Xiaomi"))
        assertEquals(AlarmKillingBrand.XIAOMI, AlarmKillingBrand.of("POCO"))
        assertEquals(AlarmKillingBrand.HUAWEI, AlarmKillingBrand.of("HONOR"))
        assertEquals(AlarmKillingBrand.OPPO, AlarmKillingBrand.of("OnePlus"))
        assertEquals(AlarmKillingBrand.VIVO, AlarmKillingBrand.of("vivo"))
    }

    @Test
    fun `phones that keep alarms get no battery step`() {
        assertNull(AlarmKillingBrand.of("Google"))
        assertNull(AlarmKillingBrand.of("motorola"))
        assertNull(AlarmKillingBrand.of("robolectric"))
    }
}
