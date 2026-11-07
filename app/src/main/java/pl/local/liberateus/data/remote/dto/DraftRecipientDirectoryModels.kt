package pl.local.liberateus.data.remote.dto

data class DraftRecipientGroup(
    val id: String,
    val name: String,
    val path: String
)

data class SelectableDraftRecipient(
    val accountId: String,
    val name: String,
    val classLabel: String? = null
)

/**
 * Only directory endpoints supported by the ordinary
 * schoolReceivers draft format.
 */
fun recipientGroupEndpoint(id: String): String? =
    when (id) {
        "teachers", "tutors", "headteachers",
        "schoolParentsCouncil", "librarian",
        "otherSpecialist", "careerAdvisor",
        "speechTherapist", "counsellor",
        "specialCounsellor", "psychologist",
        "educationalTherapist",
        "sensoryIntegrationTherapist",
        "surdopedagogue", "typhlopedagogue",
        "pedagogue", "admin", "secretary",
        "sadmin" ->
            "/receivers/groups/school-employees"

        "students", "parents", "guardians",
        "parents,guardians", "parentsCouncil" ->
            "/receivers/groups/students-and-attendants"

        "classParents" ->
            "/receivers/groups/class-parents"

        else -> null
    }

/**
 * The web interface adds virtual school accounts
 * for these two recipient groups.
 */
fun recipientTypeQuery(id: String): String =
    when (id) {
        "students" ->
            "students,virtualStudents"
        "parents,guardians" ->
            "parents,guardians,virtualParents,virtualGuardians"
        else -> id
    }
