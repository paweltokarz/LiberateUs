package pl.local.liberateus.data

import android.content.Context
import android.util.Log
import android.util.Base64

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.local.liberateus.data.remote.dto.DraftWriteResult
import pl.local.liberateus.data.remote.dto.DraftWritePayload
import pl.local.liberateus.data.remote.dto.encodeDraftText
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class LoginResult {
    data object Success : LoginResult()
    data object InvalidCredentials : LoginResult()
    data object CaptchaRequired : LoginResult()
    data class Failure(val message: String) : LoginResult()
}

data class MessagePreview(
    val id: String,
    val sender: String,
    val topic: String,
    val sendDate: String,
    val unread: Boolean
)

data class FullMessage(
    val id: String,
    val sender: String,
    val topic: String,
    val sendDate: String,
    val content: String
)

data class DraftPreview(
    val id: String,
    val ownerId: String?,
    val parentMessageId: String?,
    val dateAddedRaw: String?,
    val deleted: Boolean,
    val hasAttachment: Boolean,
    val topic: String,
    val body: String,
    val receiversJson: String,
    val type: String?
)

data class DraftPage(
    val total: Int,
    val drafts: List<DraftPreview>
)

data class SentMessagePreview(
    val id: String,
    val receiver: String,
    val receiverAccountId: String?,
    val receiverUuid: String?,
    val topic: String,
    val body: String,
    val sendDateRaw: String,
    val hasAttachment: Boolean,
    val category: String?
)

data class SentMessagePage(
    val total: Int,
    val messages: List<SentMessagePreview>
)



private class MemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, newCookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }

        newCookies.forEach { cookie ->
            cookies.removeAll {
                it.name == cookie.name &&
                    it.domain == cookie.domain &&
                    it.path == cookie.path
            }
            cookies += cookie
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
    }

    @Synchronized
    fun hasCookie(name: String, url: HttpUrl): Boolean =
        loadForRequest(url).any { it.name == name }

    @Synchronized
    fun debugSummary(): String =
        cookies.joinToString(" | ") {
            "${it.name}@${it.domain}${it.path}"
        }

    @Synchronized
    fun exportJson(): String {
        val array = JSONArray()

        cookies.forEach { cookie ->
            array.put(
                JSONObject()
                    .put("name", cookie.name)
                    .put("value", cookie.value)
                    .put("domain", cookie.domain)
                    .put("path", cookie.path)
                    .put("expiresAt", cookie.expiresAt)
                    .put("secure", cookie.secure)
                    .put("httpOnly", cookie.httpOnly)
                    .put("hostOnly", cookie.hostOnly)
            )
        }

        return array.toString()
    }

    @Synchronized
    fun importJson(json: String) {
        val array = JSONArray(json)

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)

            val builder = Cookie.Builder()
                .name(item.getString("name"))
                .value(item.getString("value"))
                .path(item.optString("path", "/"))
                .expiresAt(
                    item.optLong(
                        "expiresAt",
                        Long.MAX_VALUE
                    )
                )

            val domain = item.getString("domain")

            if (item.optBoolean("hostOnly", false)) {
                builder.hostOnlyDomain(domain)
            } else {
                builder.domain(domain)
            }

            if (item.optBoolean("secure", false)) {
                builder.secure()
            }

            if (item.optBoolean("httpOnly", false)) {
                builder.httpOnly()
            }

            val cookie = builder.build()

            cookies.removeAll {
                it.name == cookie.name &&
                    it.domain == cookie.domain &&
                    it.path == cookie.path
            }

            cookies += cookie
        }
    }

    @Synchronized
    fun keepOnlyDeviceCookie() {
        cookies.removeAll {
            !(
                it.name == "DeviceCookie" &&
                    it.domain.endsWith(
                        "api.librus.pl"
                    )
            )
        }
    }
}

class LibrusAuthClient(context: Context) {

    companion object {
        private const val PORTAL_LOGIN =
            "https://synergia.librus.pl/loguj/portalRodzina"

        private const val AUTH_URL =
            "https://api.librus.pl/OAuth/Authorization?client_id=46"

        private const val AUTH_WITH_SCOPE =
            "https://api.librus.pl/OAuth/Authorization" +
                "?client_id=46&response_type=code&scope=mydata"

        private const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/146.0.0.0 Safari/537.36"

        private val CAPTCHA_MARKERS =
            listOf("captcha", "recaptcha", "g-recaptcha", "hcaptcha")

        private val RETRY_LATER =
            setOf(429, 502, 503, 504)
    }

    private val cookieJar = MemoryCookieJar()

    private val secureStore =
        SecureLoginStore(context.applicationContext)

    private val messageCheckLog =
        MessageCheckLog(context.applicationContext)

    init {
        secureStore.loadCookies()?.let { json ->
            runCatching {
                cookieJar.importJson(json)
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        /*
         * connect/read/write timeout nie ograniczają całego
         * życia requestu. callTimeout jest naszym twardym
         * deadlinem dla pojedynczego requestu HTTP.
         */
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val redirectClient = client.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            val setCookies = response.headers("Set-Cookie")
                .map { it.substringBefore("=") }
                .joinToString(",")

            Log.i(
                "LibrusDiag",
                "${request.method} -> HTTP ${response.code} " +
                    "redirect=${response.header("Location") != null}"
            )

            response
        }
        .build()

    fun login(username: String, password: String): LoginResult {
        cookieJar.keepOnlyDeviceCookie()

        return try {
            // 1. portalRodzina -> OAuth Authorization
            val authorizationUrl = client.newCall(
                Request.Builder()
                    .url(PORTAL_LOGIN)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
            ).execute().use { response ->
                rejectRetryLater(response.code)

                val location = response.header("Location")
                    ?: return LoginResult.Failure(
                        "portalRodzina nie zwrócił Location"
                    )

                response.request.url.resolve(location)
                    ?: return LoginResult.Failure(
                        "Nieprawidłowy adres Authorization"
                    )
            }

            // 2. Ustawienie cookies po stronie api.librus.pl
            client.newCall(
                Request.Builder()
                    .url(authorizationUrl)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
            ).execute().use { response ->
                rejectRetryLater(response.code)
                response.body?.string()
            }

            // 3. Login + hasło
            val form = FormBody.Builder()
                .add("action", "login")
                .add("login", username)
                .add("pass", password)
                .build()

            val goTo = client.newCall(
                Request.Builder()
                    .url(AUTH_URL)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.9,pl;q=0.8")
                    .header("Cache-Control", "no-cache")
                    .header("Origin", "https://api.librus.pl")
                    .header("Pragma", "no-cache")
                    .header("Referer", AUTH_URL)
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "same-origin")
                    .header("User-Agent", USER_AGENT)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .post(form)
                    .build()
            ).execute().use { response ->
                rejectRetryLater(response.code)

                val text = response.body
                    ?.string()
                    .orEmpty()
                    .removePrefix("\uFEFF")

                if (containsCaptcha(text)) {
                    return LoginResult.CaptchaRequired
                }

                val json = try {
                    JSONObject(text)
                } catch (_: Exception) {
                    return LoginResult.Failure(
                        "Odpowiedź logowania nie jest JSON"
                    )
                }

                val value = json.optString("goTo")

                if (value.isBlank()) {
                    return LoginResult.InvalidCredentials
                }

                value
            }

            // 4. Ręczny redirect chain
            val scopeBase = AUTH_WITH_SCOPE.toHttpUrl()
            var currentUrl = scopeBase.resolve(goTo)
                ?: return LoginResult.Failure(
                    "Nieprawidłowy adres goTo"
                )

            var finished = false

            for (hop in 0 until 10) {
                val nextUrl = client.newCall(
                    Request.Builder()
                        .url(currentUrl)
                        .header("User-Agent", USER_AGENT)
                        .get()
                        .build()
                ).execute().use { response ->
                    rejectRetryLater(response.code)

                    val text = response.body?.string().orEmpty()

                    if (containsCaptcha(text)) {
                        return LoginResult.CaptchaRequired
                    }

                    val location = response.header("Location")

                    if (location == null) {
                        null
                    } else {
                        response.request.url.resolve(location)
                            ?: return LoginResult.Failure(
                                "Nieprawidłowy redirect OAuth"
                            )
                    }
                }

                if (nextUrl == null) {
                    finished = true
                    break
                }

                currentUrl = nextUrl
            }

            if (!finished) {
                return LoginResult.Failure(
                    "Przekroczono limit redirectów OAuth"
                )
            }

            val synergiaUrl =
                "https://synergia.librus.pl/".toHttpUrl()

            if (!cookieJar.hasCookie("oauth_token", synergiaUrl)) {
                return LoginResult.Failure(
                    "Login zakończył się bez oauth_token"
                )
            }

            secureStore.saveCredentials(
                username,
                password
            )

            persistCookies()

            LoginResult.Success

        } catch (e: IOException) {
            LoginResult.Failure(
                "Błąd sieci: ${e.message ?: "nieznany"}"
            )
        } catch (e: Exception) {
            LoginResult.Failure(
                e.message ?: e.javaClass.simpleName
            )
        }
    }

    fun fetchInbox(
        limit: Int = 10,
        source: MessageFetchSource =
            MessageFetchSource.UNKNOWN
    ): List<MessagePreview> {
        val startedAt =
            System.currentTimeMillis()

        messageCheckLog.write(
            source = source,
            event = "FETCH_START",
            details = "limit=$limit"
        )

        return try {
            if (!bootstrapMessages()) {
                throw IOException(
                    "Brak dostępu do modułu Wiadomości"
                )
            }

            val request = Request.Builder()
                .url(
                    "https://wiadomosci.librus.pl/api/" +
                        "inbox/messages?limit=$limit"
                )
                .header(
                    "Accept",
                    "application/json"
                )
                .header(
                    "User-Agent",
                    USER_AGENT
                )
                .get()
                .build()

            val messages =
                redirectClient
                    .newCall(request)
                    .execute()
                    .use { response ->
                        rejectRetryLater(
                            response.code
                        )

                        if (!response.isSuccessful) {
                            throw IOException(
                                "Wiadomości: HTTP " +
                                    response.code
                            )
                        }

                        val body =
                            response.body
                                ?.string()
                                .orEmpty()
                                .trim()

                        val array =
                            if (
                                body.startsWith("[")
                            ) {
                                JSONArray(body)
                            } else {
                                JSONObject(body)
                                    .optJSONArray("data")
                                    ?: JSONArray()
                            }

                        buildList {
                            for (
                                i in 0 until
                                    array.length()
                            ) {
                                val item =
                                    array.optJSONObject(i)
                                        ?: continue

                                val sender =
                                    item
                                        .optString(
                                            "senderName"
                                        )
                                        .ifBlank {
                                            listOf(
                                                item.optString(
                                                    "senderFirstName"
                                                ),
                                                item.optString(
                                                    "senderLastName"
                                                )
                                            )
                                                .filter {
                                                    it.isNotBlank()
                                                }
                                                .joinToString(
                                                    " "
                                                )
                                        }

                                add(
                                    MessagePreview(
                                        id =
                                            item.opt(
                                                "messageId"
                                            )
                                                ?.toString()
                                                .orEmpty(),

                                        sender =
                                            sender.ifBlank {
                                                "Nieznany nadawca"
                                            },

                                        topic =
                                            item.optString(
                                                "topic"
                                            ),

                                        sendDate =
                                            item.optString(
                                                "sendDate"
                                            ),

                                        unread =
                                            item.isNull(
                                                "readDate"
                                            ) ||
                                                item
                                                    .optString(
                                                        "readDate"
                                                    )
                                                    .isBlank()
                                    )
                                )
                            }
                        }
                    }

            val duration =
                System.currentTimeMillis() -
                    startedAt

            messageCheckLog.write(
                source = source,
                event = "FETCH_OK",
                details =
                    "count=${messages.size} " +
                        "durationMs=$duration"
            )

            messages

        } catch (e: Exception) {
            val duration =
                System.currentTimeMillis() -
                    startedAt

            messageCheckLog.write(
                source = source,
                event = "FETCH_ERROR",
                details =
                    "durationMs=$duration " +
                        "error=" +
                        e.javaClass.simpleName
            )

            throw e
        }
    }


    /**
     * Creates a normal draft, never sends a message.
     *
     * Intentionally no automatic retry: a network
     * failure after server commit must not generate
     * duplicate drafts.
     */

    /**
     * Issues at most one HTTP POST. Calling code must confirm the result
     * through the Sent folder. Network failures can leave an uncertain result.
     */
    fun sendMessageOnce(
        topic: String,
        content: String,
        receiversJson: String
    ): Int {
        require(topic.isNotBlank()) { "Temat jest wymagany" }
        require(content.isNotBlank()) { "Treść jest wymagana" }

        val incoming = JSONObject(receiversJson)
        val list = incoming.optJSONArray("schoolReceivers")
            ?: throw IllegalArgumentException("Brak odbiorców")
        require(list.length() > 0) { "Wybierz odbiorców" }

        val ids = mutableSetOf<String>()
        val normalized = org.json.JSONArray()
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i)
                ?: throw IllegalArgumentException("Nieprawidłowy odbiorca")
            val accountId = item.optString("accountId").trim()
            require(accountId.isNotEmpty()) { "Brak accountId odbiorcy" }
            require(ids.add(accountId)) { "Odbiorca występuje dwukrotnie" }
            normalized.put(JSONObject().put("accountId", accountId))
        }

        if (!bootstrapMessages()) {
            throw IOException("Brak dostępu do Wiadomości Librus")
        }

        val data = JSONObject().apply {
            put(
                "receivers",
                JSONObject().put("schoolReceivers", normalized)
            )
            put("topic", encodeDraftText(topic))
            put("content", encodeDraftText(content))
            put("storageId", JSONObject.NULL)
            put("category", "normal")
        }

        val request = Request.Builder()
            .url("https://wiadomosci.librus.pl/api/messages")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .post(
                data.toString().toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )
            )
            .build()

        val client = redirectClient.newBuilder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (isDefinitiveSendRejection(response.code)) {
                    throw LibrusSendRejectedException(
                        response.code,
                        readableSendRejection(response.body?.string())
                    )
                }
                throw IOException(
                    "Niepewny wynik żądania (HTTP ${response.code})."
                )
            }
            return response.code
        }
    }

    fun createDraft(
        payload: DraftWritePayload
    ): DraftWriteResult =
        executeDraftWrite(
            endpoint = "drafts/newOne",
            method = "POST",
            payload = payload,
            existingId = null
        )

    fun updateDraft(
        draftId: String,
        payload: DraftWritePayload
    ): DraftWriteResult {
        require(
            draftId.isNotBlank() &&
                draftId.all { it in '0'..'9' }
        )

        return executeDraftWrite(
            endpoint = "drafts/newOne/$draftId",
            method = "PUT",
            payload = payload,
            existingId = draftId
        )
    }

    private fun executeDraftWrite(
        endpoint: String,
        method: String,
        payload: DraftWritePayload,
        existingId: String?
    ): DraftWriteResult {
        require(
            method == "POST" || method == "PUT"
        )

        /*
         * Validate and construct JSON before doing
         * anything on the network.
         */
        val receivers =
            JSONObject(payload.receiversJson)

        val json =
            JSONObject().apply {
                put("receivers", receivers)
                put("topic", encodeDraftText(payload.topic))
                put("content", encodeDraftText(payload.content))
                put(
                    "storageId",
                    payload.storageId ?: JSONObject.NULL
                )
            }

        if (!bootstrapMessages()) {
            throw IOException(
                "Brak dostępu do modułu Wiadomości"
            )
        }

        val body =
            json.toString().toRequestBody(
                "application/json; charset=utf-8"
                    .toMediaType()
            )

        val request =
            Request.Builder()
                .url(
                    "https://wiadomosci.librus.pl/api/" +
                        endpoint
                )
                .header(
                    "Accept",
                    "application/json"
                )
                .header(
                    "User-Agent",
                    USER_AGENT
                )
                .method(
                    method,
                    body
                )
                .build()

        /*
         * One request only.
         * Do not retry an uncertain write outcome.
         */
        return redirectClient
            .newCall(request)
            .execute()
            .use { response ->

                rejectRetryLater(response.code)

                val responseText =
                    response.body
                        ?.string()
                        .orEmpty()

                if (!response.isSuccessful) {
                    throw IOException(
                        "HTTP ${response.code} " +
                            "podczas zapisu roboczej"
                    )
                }

                val identifier =
                    if (responseText.isBlank()) {
                        null
                    } else {
                        val parsed =
                            try {
                                JSONObject(responseText)
                            } catch (error: Exception) {
                                throw IOException(
                                    "Nieprawidłowa odpowiedź " +
                                        "po zapisie roboczej; " +
                                        "nie ponawiaj automatycznie",
                                    error
                                )
                            }

                        val value =
                            parsed
                                .optJSONObject("data")
                                ?.opt("identifier")

                        if (
                            value == null ||
                            value == JSONObject.NULL
                        ) {
                            null
                        } else {
                            value.toString()
                        }
                    }

                DraftWriteResult(
                    identifier =
                        identifier ?: existingId,
                    httpCode = response.code
                )
            }
    }


    /**
     * Delete one existing draft.
     * Does not send a message.
     * Never retry automatically.
     */
    fun deleteDraft(draftId: String) {
        require(
            draftId.isNotBlank() &&
                draftId.all { it in '0'..'9' }
        ) {
            "Draft identifier must be numeric"
        }

        if (!bootstrapMessages()) {
            throw IOException(
                "Brak dostępu do modułu Wiadomości"
            )
        }

        val request = Request.Builder()
            .url(
                "https://wiadomosci.librus.pl/api/" +
                    "drafts/$draftId"
            )
            .header(
                "Accept",
                "application/json"
            )
            .header(
                "User-Agent",
                USER_AGENT
            )
            .delete()
            .build()

        redirectClient
            .newCall(request)
            .execute()
            .use { response ->
                rejectRetryLater(response.code)

                if (!response.isSuccessful) {
                    throw IOException(
                        "HTTP ${response.code} " +
                            "podczas usuwania roboczej. " +
                            "Sprawdź listę przed ponowieniem."
                    )
                }
            }
    }

    /**
     * GET-only recipient directory.
     * No endpoint for sending messages.
     */
    /**
     * Read-only recipient category list.
     */
    fun fetchDraftRecipientTypes(): String {
        if (!bootstrapMessages()) {
            throw IOException("Brak modułu Wiadomości")
        }

        val request = Request.Builder()
            .url(
                "https://wiadomosci.librus.pl/api/" +
                    "receivers/types?includeClass=true"
            )
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return redirectClient
            .newCall(request)
            .execute()
            .use { response ->
                rejectRetryLater(response.code)
                if (!response.isSuccessful) {
                    throw IOException(
                        "HTTP ${response.code} — kategorie odbiorców"
                    )
                }
                response.body?.string()
                    ?: throw IOException("Pusta lista kategorii")
            }
    }

    fun fetchDraftRecipientGroup(
        path: String,
        receiverType: String
    ): String {
        val allowed = setOf(
            "/receivers/groups/school-employees",
            "/receivers/groups/students-and-attendants",
            "/receivers/groups/class-parents",
            "/receivers/groups/jst-teachers",
            "/receivers/groups/jst-students",
            "/receivers/groups/jst-parents",
            "/receivers/groups/jst-school-employees",
            "/receivers/groups/kindergarteners"
        )

        require(path in allowed) {
            "Unsupported recipient directory"
        }
        require(
            receiverType.isNotBlank() &&
                receiverType.length <= 100 &&
                receiverType.all {
                    it in 'a'..'z' ||
                        it in 'A'..'Z' ||
                        it in '0'..'9' ||
                        it == ','
                }
        ) {
            "Invalid receiver category"
        }

        if (!bootstrapMessages()) {
            throw IOException(
                "Brak dostępu do Wiadomości"
            )
        }

        val request = Request.Builder()
            .url(
                (
                    "https://wiadomosci.librus.pl/api" +
                        path
                ).toHttpUrl()
                    .newBuilder()
                    .addQueryParameter(
                        "receiverType",
                        receiverType
                    )
                    .build()
            )
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return redirectClient
            .newCall(request)
            .execute()
            .use { response ->
                rejectRetryLater(response.code)

                if (!response.isSuccessful) {
                    throw IOException(
                        "HTTP ${response.code} podczas " +
                            "pobierania odbiorców"
                    )
                }

                response.body?.string()
                    ?: throw IOException(
                        "Pusta lista odbiorców"
                    )
            }
    }

    fun fetchDrafts(
        limit: Int = 100
    ): DraftPage {
        require(
            limit > 0
        ) {
            "limit must be greater than zero"
        }

        if (!bootstrapMessages()) {
            throw IOException(
                "Brak dostępu do modułu Wiadomości"
            )
        }

        val request =
            Request.Builder()
                .url(
                    "https://wiadomosci.librus.pl/api/" +
                        "drafts?limit=$limit"
                )
                .header(
                    "Accept",
                    "application/json"
                )
                .header(
                    "User-Agent",
                    USER_AGENT
                )
                .get()
                .build()

        redirectClient
            .newCall(
                request
            )
            .execute()
            .use {
                response ->

                rejectRetryLater(
                    response.code
                )

                val text =
                    response
                        .body
                        ?.string()
                        .orEmpty()

                if (!response.isSuccessful) {
                    throw IOException(
                        "HTTP ${response.code} podczas pobierania roboczych"
                    )
                }

                val root =
                    JSONObject(
                        text
                    )

                val array =
                    root.optJSONArray(
                        "data"
                    )
                        ?: JSONArray()

                val drafts =
                    mutableListOf<
                        DraftPreview
                    >()

                for (
                    index in
                    0 until array.length()
                ) {
                    val item =
                        array.optJSONObject(
                            index
                        )
                            ?: continue

                    if (
                        !item.has(
                            "identifier"
                        ) ||
                        item.isNull(
                            "identifier"
                        )
                    ) {
                        continue
                    }

                    val id =
                        item
                            .optLong(
                                "identifier"
                            )
                            .toString()

                    val dateAddedRaw =
                        when (
                            val value =
                                item.opt(
                                    "dateAdded"
                                )
                        ) {
                            null,
                            JSONObject.NULL ->
                                null

                            else ->
                                value.toString()
                        }

                    val receiversJson =
                        when (
                            val value =
                                item.opt(
                                    "receivers"
                                )
                        ) {
                            is JSONArray ->
                                value.toString()

                            is JSONObject ->
                                value.toString()

                            else ->
                                "[]"
                        }

                    drafts +=
                        DraftPreview(
                            id =
                                id,
                            ownerId =
                                item.nullableIdentifier(
                                    "ownerIdentifier"
                                ),
                            parentMessageId =
                                item.nullableIdentifier(
                                    "parentMessageIdentifier"
                                ),
                            dateAddedRaw =
                                dateAddedRaw,
                            deleted =
                                item.optBoolean(
                                    "deleted",
                                    false
                                ),
                            hasAttachment =
                                item.optBoolean(
                                    "isAnyFileAttached",
                                    false
                                ),
                            topic =
                                item.optString(
                                    "topic",
                                    ""
                                ),
                            body =
                                decodeDraftBody(
                                    item.optString(
                                        "body",
                                        ""
                                    )
                                ),
                            receiversJson =
                                receiversJson,
                            type =
                                item
                                    .optString(
                                        "type",
                                        ""
                                    )
                                    .takeIf {
                                        it.isNotBlank()
                                    }
                        )
                }

                return DraftPage(
                    total =
                        root.optInt(
                            "total",
                            drafts.size
                        ),
                    drafts =
                        drafts
                )
            }
    }

    private fun JSONObject.nullableIdentifier(
        name: String
    ): String? {
        if (
            !has(name) ||
            isNull(name)
        ) {
            return null
        }

        return when (
            val value =
                opt(name)
        ) {
            is Number ->
                value.toLong()
                    .toString()

            is String ->
                value
                    .trim()
                    .takeIf {
                        it.isNotEmpty()
                    }

            else ->
                value
                    ?.toString()
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    }
        }
    }

    private fun decodeDraftBody(
        value: String
    ): String {
        if (
            value.isBlank()
        ) {
            return ""
        }

        return try {
            val decoded =
                Base64.decode(
                    value,
                    Base64.DEFAULT
                )

            val normalizedInput =
                value
                    .filterNot {
                        it.isWhitespace()
                    }
                    .trimEnd('=')

            val normalizedOutput =
                Base64
                    .encodeToString(
                        decoded,
                        Base64.NO_WRAP
                    )
                    .trimEnd('=')

            if (
                normalizedInput !=
                normalizedOutput
            ) {
                value
            } else {
                decoded.toString(
                    Charsets.UTF_8
                )
            }
        } catch (
            ignored:
                IllegalArgumentException
        ) {
            value
        }
    }



    /**
     * Read-only Librus outbox.
     *
     * Live-confirmed endpoint:
     *
     * GET /api/outbox/messages
     */
    fun fetchSentMessages(
        limit: Int = 100
    ): SentMessagePage {
        require(
            limit > 0
        ) {
            "limit must be greater than zero"
        }

        if (!bootstrapMessages()) {
            throw IOException(
                "Brak dostępu do modułu Wiadomości"
            )
        }

        val request =
            Request.Builder()
                .url(
                    "https://wiadomosci.librus.pl/api/" +
                        "outbox/messages?limit=$limit"
                )
                .header(
                    "Accept",
                    "application/json"
                )
                .header(
                    "User-Agent",
                    USER_AGENT
                )
                .get()
                .build()

        redirectClient
            .newCall(
                request
            )
            .execute()
            .use {
                response ->

                rejectRetryLater(
                    response.code
                )

                val text =
                    response
                        .body
                        ?.string()
                        .orEmpty()

                if (
                    !response.isSuccessful
                ) {
                    throw IOException(
                        "HTTP ${response.code} podczas pobierania wysłanych"
                    )
                }

                val root =
                    JSONObject(
                        text
                    )

                val array =
                    root.optJSONArray(
                        "data"
                    )
                        ?: JSONArray()

                val messages =
                    mutableListOf<
                        SentMessagePreview
                    >()

                for (
                    index in
                    0 until array.length()
                ) {
                    val item =
                        array.optJSONObject(
                            index
                        )
                            ?: continue

                    val id =
                        item
                            .opt(
                                "messageId"
                            )
                            ?.toString()
                            ?.trim()
                            .orEmpty()

                    if (
                        id.isBlank()
                    ) {
                        continue
                    }

                    val receiver =
                        item
                            .optString(
                                "receiverName"
                            )
                            .ifBlank {
                                listOf(
                                    item.optString(
                                        "receiverFirstName"
                                    ),
                                    item.optString(
                                        "receiverLastName"
                                    )
                                )
                                    .filter {
                                        it.isNotBlank()
                                    }
                                    .joinToString(
                                        " "
                                    )
                            }
                            .ifBlank {
                                "Nieznany odbiorca"
                            }

                    messages +=
                        SentMessagePreview(
                            id =
                                id,

                            receiver =
                                receiver,

                            receiverAccountId =
                                item.nullableIdentifier(
                                    "otherNodeAccountId"
                                ),

                            receiverUuid =
                                item
                                    .optString(
                                        "otherNodeUuid",
                                        ""
                                    )
                                    .trim()
                                    .takeIf {
                                        it.isNotEmpty()
                                    },

                            topic =
                                item.optString(
                                    "topic",
                                    ""
                                ),

                            body =
                                decodeDraftBody(
                                    item.optString(
                                        "content",
                                        ""
                                    )
                                ),

                            sendDateRaw =
                                item.optString(
                                    "sendDate",
                                    ""
                                ),

                            hasAttachment =
                                item.optBoolean(
                                    "isAnyFileAttached",
                                    false
                                ),

                            category =
                                item
                                    .optString(
                                        "category",
                                        ""
                                    )
                                    .trim()
                                    .takeIf {
                                        it.isNotEmpty()
                                    }
                        )
                }

                return SentMessagePage(
                    total =
                        root.optInt(
                            "total",
                            messages.size
                        ),

                    messages =
                        messages
                )
            }
    }


    fun fetchMessage(
        messageId: String,
        mailbox: String = "inbox"
    ): FullMessage {
        if (!bootstrapMessages()) {
            throw IOException(
                "Brak dostępu do modułu Wiadomości"
            )
        }

        val request = Request.Builder()
            .url(
                "https://wiadomosci.librus.pl/api/" +
                    "$mailbox/messages/$messageId"
            )
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        redirectClient.newCall(request).execute().use { response ->
            rejectRetryLater(response.code)

            if (!response.isSuccessful) {
                throw IOException(
                    "Wiadomość: HTTP ${response.code}"
                )
            }

            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            val data = root.optJSONObject("data") ?: root

            val sender = data.optString("senderName").ifBlank {
                listOf(
                    data.optString("senderFirstName"),
                    data.optString("senderLastName")
                )
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            }

            val encoded = when {
                data.has("Message") ->
                    data.optString("Message")

                data.has("message") ->
                    data.optString("message")

                data.has("content") ->
                    data.optString("content")

                else -> ""
            }

            return FullMessage(
                id = messageId,
                sender = sender.ifBlank {
                    "Nieznany nadawca"
                },
                topic = data.optString("topic")
                    .ifBlank {
                        data.optString("Topic")
                    },
                sendDate = data.optString("sendDate")
                    .ifBlank {
                        data.optString("SendDate")
                    },
                content = decodeMessageBody(encoded)
            )
        }
    }

    private fun decodeMessageBody(
        encoded: String
    ): String {
        if (encoded.isBlank()) {
            return ""
        }

        val decoded = try {
            String(
                Base64.decode(
                    encoded,
                    Base64.DEFAULT
                ),
                Charsets.UTF_8
            )
        } catch (_: Exception) {
            encoded
        }

        val cdata = Regex(
            """<!\[CDATA\[(.*?)]]>""",
            setOf(
                RegexOption.DOT_MATCHES_ALL,
                RegexOption.IGNORE_CASE
            )
        ).find(decoded)
            ?.groupValues
            ?.getOrNull(1)

        return cdata ?: decoded
    }

    private fun bootstrapMessages(): Boolean {
        var currentUrl =
            "https://synergia.librus.pl/wiadomosci3".toHttpUrl()

        for (hop in 0 until 10) {
            val result = client.newCall(
                Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
            ).execute().use { response ->
                rejectRetryLater(response.code)

                val body = response.body?.string().orEmpty()
                val location = response.header("Location")

                Log.i(
                    "LibrusDiag",
                    "BOOTSTRAP hop=$hop status=${response.code} " +
                        "redirect=${location != null}"
                )

                if (containsCaptcha(body)) {
                    throw IOException(
                        "CAPTCHA podczas uruchamiania Wiadomości"
                    )
                }

                if (location == null) {
                    if (!response.isSuccessful) {
                        throw IOException(
                            "Bootstrap Wiadomości: HTTP ${response.code}"
                        )
                    }

                    val denied = body.contains(
                        "Brak dostępu",
                        ignoreCase = true
                    )

                    Log.i(
                        "LibrusDiag",
                        "BOOTSTRAP finished denied=$denied " +
                            "bodyLength=${body.length}"
                    )

                    if (!denied) {
                        persistCookies()
                    }

                    return !denied
                }

                response.request.url.resolve(location)
                    ?: throw IOException(
                        "Nieprawidłowy redirect Wiadomości"
                    )
            }

            currentUrl =
                if (
                    result.host == "wiadomosci.librus.pl" &&
                    result.scheme == "http"
                ) {
                    Log.i(
                        "LibrusDiag",
                        "Upgrading Wiadomości redirect HTTP -> HTTPS"
                    )

                    result.newBuilder()
                        .scheme("https")
                        .build()
                } else {
                    result
                }
        }

        throw IOException(
            "Bootstrap Wiadomości: za dużo przekierowań"
        )
    }

    fun hasSavedCredentials(): Boolean =
        secureStore.loadCredentials() != null

    fun savedUsername(): String? =
        secureStore.loadCredentials()?.username

    fun hasRestoredSession(): Boolean =
        cookieJar.hasCookie(
            "oauth_token",
            "https://synergia.librus.pl/".toHttpUrl()
        )

    fun refreshOAuthToken(): Boolean {
        val synergiaUrl =
            "https://synergia.librus.pl/".toHttpUrl()

        /*
         * Nie wymagamy istniejącego oauth_token.
         * /refreshToken może właśnie służyć do jego
         * ponownego wystawienia na podstawie pozostałej sesji.
         */
        var currentUrl =
            "https://synergia.librus.pl/refreshToken"
                .toHttpUrl()

        for (step in 0 until 6) {
            val request =
                Request.Builder()
                    .url(currentUrl)
                    .header(
                        "User-Agent",
                        USER_AGENT
                    )
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml," +
                            "application/json;q=0.9,*/*;q=0.8"
                    )
                    .header(
                        "Referer",
                        "https://synergia.librus.pl/"
                    )
                    .get()
                    .build()

            var nextUrl: okhttp3.HttpUrl? = null
            var terminal = false
            var httpSuccess = false

            client
                .newCall(request)
                .execute()
                .use { response ->
                    Log.i(
                        "LibrusRefresh",
                        "step=$step " +
                            "code=${response.code} " +
                            "redirect=${response.header("Location") != null}"
                    )

                    rejectRetryLater(
                        response.code
                    )

                    when {
                        response.code in 300..399 -> {
                            val location =
                                response.header("Location")
                                    ?: return false

                            val resolved =
                                response.request.url
                                    .resolve(location)
                                    ?: return false

                            if (
                                resolved.scheme == "http" &&
                                resolved.host ==
                                    "wiadomosci.librus.pl"
                            ) {
                                nextUrl =
                                    resolved
                                        .newBuilder()
                                        .scheme("https")
                                        .build()
                            } else {
                                if (
                                    resolved.scheme != "https"
                                ) {
                                    return false
                                }

                                nextUrl = resolved
                            }
                        }

                        response.isSuccessful -> {
                            terminal = true
                            httpSuccess = true
                        }

                        else -> {
                            terminal = true
                        }
                    }
                }

            val hasOAuthToken =
                cookieJar.hasCookie(
                    "oauth_token",
                    synergiaUrl
                )

            Log.i(
                "LibrusRefresh",
                "step=$step afterResponse " +
                    "hasOauth=$hasOAuthToken"
            )

            if (
                hasOAuthToken &&
                (
                    terminal ||
                    nextUrl != null
                )
            ) {
                persistCookies()
                return true
            }

            if (terminal) {
                return httpSuccess &&
                    hasOAuthToken
            }

            currentUrl =
                nextUrl ?: return false
        }

        return false
    }

    fun loginWithSavedCredentials(): LoginResult {
        val saved = secureStore.loadCredentials()
            ?: return LoginResult.Failure(
                "Brak zapisanych danych logowania"
            )

        return login(
            saved.username,
            saved.password
        )
    }

    fun resumeSavedLogin(): LoginResult {
        if (hasRestoredSession()) {
            return LoginResult.Success
        }

        return loginWithSavedCredentials()
    }

    fun clearSavedLogin() {
        cookieJar.clear()
        secureStore.clear()
    }

    private fun persistCookies() {
        secureStore.saveCookies(
            cookieJar.exportJson()
        )
    }

    private fun containsCaptcha(text: String): Boolean {
        val low = text.lowercase()
        return CAPTCHA_MARKERS.any { it in low }
    }

    private fun rejectRetryLater(code: Int) {
        if (code in RETRY_LATER) {
            throw IOException("Librus chwilowo niedostępny: HTTP $code")
        }
    }

}
