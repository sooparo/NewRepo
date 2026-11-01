package com.sooparo.schedulecatcher.core

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 최소 기능 CalDAV 클라이언트 (RFC 4791).
 * 네이버 캘린더는 CalDAV 를 지원한다: https://caldav.calendar.naver.com
 *
 * 블로킹 호출이므로 Android 에서는 반드시 백그라운드 스레드에서 호출할 것.
 */
class CalDavClient(
    username: String,
    password: String,
    private val baseUrl: String = NAVER_CALDAV_URL,
    private val http: OkHttpClient = defaultClient(),
) {

    data class Calendar(val url: String, val displayName: String)

    class CalDavException(message: String, val code: Int? = null) : IOException(message)

    private val authHeader = Credentials.basic(username, password, Charsets.UTF_8)

    /** 계정의 일정(VEVENT) 캘린더 목록을 찾는다. */
    fun discoverCalendars(): List<Calendar> {
        val base = baseUrl.toHttpUrl()

        val principal = findHref(base, CURRENT_USER_PRINCIPAL_BODY, "current-user-principal")
            ?: base.resolve("/.well-known/caldav")?.let {
                findHref(it, CURRENT_USER_PRINCIPAL_BODY, "current-user-principal")
            }

        val home = principal?.let { findHref(it, CALENDAR_HOME_BODY, "calendar-home-set") }
            ?: principal
            ?: base

        val responses = propfind(home, depth = "1", body = CALENDAR_LIST_BODY)
        return responses.mapNotNull { r ->
            val resourceType = r.props["resourcetype"] ?: return@mapNotNull null
            val isCalendar = resourceType.children().any { it.namespaceURI == CALDAV_NS && it.localName == "calendar" }
            if (!isCalendar) return@mapNotNull null

            val components = r.props["supported-calendar-component-set"]
                ?.children()
                ?.filter { it.localName == "comp" }
                ?.map { it.getAttribute("name").uppercase() }
            if (components != null && components.isNotEmpty() && "VEVENT" !in components) return@mapNotNull null

            val url = r.url.toString()
            val name = r.props["displayname"]?.textContent?.trim().orEmpty()
            Calendar(url = url, displayName = name.ifEmpty { r.url.pathSegments.lastOrNull { it.isNotEmpty() } ?: url })
        }
    }

    /**
     * 일정을 생성한다.
     * @return 생성된 일정 리소스 URL
     */
    fun createEvent(calendarUrl: String, uid: String, ics: String): String {
        val collection = calendarUrl.let { if (it.endsWith("/")) it else "$it/" }.toHttpUrl()
        val eventUrl = collection.newBuilder().addPathSegment("$uid.ics").build()

        val request = Request.Builder()
            .url(eventUrl)
            .header("Authorization", authHeader)
            .header("If-None-Match", "*")
            .put(ics.toRequestBody(ICS_MEDIA_TYPE))
            .build()

        http.newCall(request).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) throw authError(resp.code)
            if (!resp.isSuccessful) {
                throw CalDavException("일정 저장 실패 (HTTP ${resp.code})", resp.code)
            }
        }
        return eventUrl.toString()
    }

    // ---------------------------------------------------------------------------------------------

    private data class DavResponse(val url: HttpUrl, val props: Map<String, Element>)

    /** PROPFIND Depth 0 으로 [prop] 안의 href 를 찾는다. 인증 오류 외의 실패는 null. */
    private fun findHref(url: HttpUrl, body: String, prop: String): HttpUrl? {
        val responses = try {
            propfind(url, depth = "0", body = body)
        } catch (e: CalDavException) {
            if (e.code == 401 || e.code == 403) throw e
            return null
        } catch (e: IOException) {
            return null
        }
        return responses.firstNotNullOfOrNull { r ->
            r.props[prop]
                ?.children()
                ?.firstOrNull { it.namespaceURI == DAV_NS && it.localName == "href" }
                ?.textContent?.trim()
                ?.let { url.resolve(it) }
        }
    }

    private fun propfind(url: HttpUrl, depth: String, body: String): List<DavResponse> {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .header("Depth", depth)
            .method("PROPFIND", body.toRequestBody(XML_MEDIA_TYPE))
            .build()

        http.newCall(request).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) throw authError(resp.code)
            if (resp.code != 207 && !resp.isSuccessful) {
                throw CalDavException("CalDAV 요청 실패 (HTTP ${resp.code}) ${url.encodedPath}", resp.code)
            }
            val xml = resp.body?.string().orEmpty()
            return parseMultistatus(resp.request.url, xml)
        }
    }

    private fun authError(code: Int) = CalDavException(
        "인증 실패 (HTTP $code): 네이버 아이디와 애플리케이션 비밀번호를 확인하세요.",
        code,
    )

    private fun parseMultistatus(requestUrl: HttpUrl, xml: String): List<DavResponse> {
        if (xml.isBlank()) return emptyList()
        val doc = secureDocumentBuilderFactory().newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val responses = doc.getElementsByTagNameNS(DAV_NS, "response")
        return (0 until responses.length).mapNotNull { i ->
            val response = responses.item(i) as Element
            val href = response.children().firstOrNull { it.namespaceURI == DAV_NS && it.localName == "href" }
                ?.textContent?.trim() ?: return@mapNotNull null
            val url = requestUrl.resolve(href) ?: return@mapNotNull null

            val props = mutableMapOf<String, Element>()
            response.children()
                .filter { it.namespaceURI == DAV_NS && it.localName == "propstat" }
                .filter { ps ->
                    val status = ps.children().firstOrNull { it.localName == "status" }?.textContent.orEmpty()
                    status.isBlank() || Regex("\\s2\\d\\d\\s").containsMatchIn(" $status ")
                }
                .flatMap { ps -> ps.children().filter { it.namespaceURI == DAV_NS && it.localName == "prop" } }
                .flatMap { it.children() }
                .forEach { props.putIfAbsent(it.localName, it) }

            DavResponse(url, props)
        }
    }

    private fun Element.children(): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    companion object {
        const val NAVER_CALDAV_URL = "https://caldav.calendar.naver.com/"

        private const val DAV_NS = "DAV:"
        private const val CALDAV_NS = "urn:ietf:params:xml:ns:caldav"

        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private val ICS_MEDIA_TYPE = "text/calendar; charset=utf-8".toMediaType()

        private val CURRENT_USER_PRINCIPAL_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:current-user-principal/></d:prop></d:propfind>
        """.trimIndent()

        private val CALENDAR_HOME_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:prop><c:calendar-home-set/></d:prop></d:propfind>
        """.trimIndent()

        private val CALENDAR_LIST_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:prop><d:resourcetype/><d:displayname/><c:supported-calendar-component-set/></d:prop>
            </d:propfind>
        """.trimIndent()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                // XXE 방지 (지원하지 않는 플랫폼에서는 무시)
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
    }
}
