package pl.local.liberateus.ui.drafts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.local.liberateus.data.remote.dto.recipientGroupEndpoint
import pl.local.liberateus.data.remote.dto.recipientTypeQuery

class DraftRecipientRouteTest {
    @Test
    fun teachersUseSchoolEmployees() {
        assertEquals(
            "/receivers/groups/school-employees",
            recipientGroupEndpoint("teachers")
        )
    }

    @Test
    fun tutorsUseSchoolEmployees() {
        assertEquals(
            "/receivers/groups/school-employees",
            recipientGroupEndpoint("tutors")
        )
    }

    @Test
    fun unsupportedGroupCannotBeRequested() {
        assertNull(recipientGroupEndpoint("unknown"))
        assertNull(recipientGroupEndpoint("../messages"))
    }

    @Test
    fun virtualStudentsAreIncluded() {
        assertEquals(
            "students,virtualStudents",
            recipientTypeQuery("students")
        )
    }
}
