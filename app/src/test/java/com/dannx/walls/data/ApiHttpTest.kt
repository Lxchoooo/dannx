package com.dannx.walls.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ApiHttpTest {
    @Test
    fun `known status codes map to typed errors`() {
        assertEquals(DannXError.SessionExpired, errorFor(401, ""))
        assertEquals(DannXError.Forbidden, errorFor(403, ""))
        assertEquals(DannXError.Server(503), errorFor(503, ""))
    }

    @Test
    fun `unexpected codes keep the server detail`() {
        val error = errorFor(418, "sin café")

        assertTrue(error is DannXError.Unexpected)
        assertTrue(error.message.orEmpty().contains("418"))
        assertTrue(error.message.orEmpty().contains("sin café"))
    }

    @Test
    fun `readBody accepts success and allowed conflicts`() {
        assertEquals("ok", HttpResult(200, "ok").readBody())
        assertEquals("dup", HttpResult(409, "dup").readBody(allowConflict = true))
    }

    @Test
    fun `readBody turns failures into errors`() {
        try {
            HttpResult(401, "").readBody()
            fail("esperaba sesión vencida")
        } catch (_: DannXError.SessionExpired) {
            // correcto
        }
        try {
            HttpResult(409, "").readBody()
            fail("esperaba un error inesperado")
        } catch (_: DannXError.Unexpected) {
            // correcto
        }
    }
}
