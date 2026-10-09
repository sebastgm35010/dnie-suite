package pe.gob.reniec.dnie.ui

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.ui.navigation.Screen

class NavigationDestinationsTest {
    @Test fun initializingHomeFirstCannotLeaveNullDestinationsInSuperclass() {
        // Accessing a nested singleton first initializes its superclass before INSTANCE.
        assertEquals("home", Screen.Home.route)
        val destinations = Screen.items
        destinations.forEach { assertNotNull(it) }
        assertEquals(listOf("home", "identity", "certificates", "signer", "vault"), destinations.map { it.route })
        assertEquals(destinations.size, destinations.map { it.route }.distinct().size)
    }
}
