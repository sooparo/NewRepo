package com.sooparo.schedulecatcher.core

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CalDavClientTest {

    private lateinit var server: MockWebServer
    private val requests = mutableListOf<RecordedRequest>()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                if (request.getHeader("Authorization") != okhttp3.Credentials.basic("user", "app-pw")) {
                    return MockResponse().setResponseCode(401)
                }
                val body = request.body.readUtf8()
                return when {
                    request.method == "PROPFIND" && "current-user-principal" in body -> multistatus(
                        "/", "<d:current-user-principal><d:href>/principals/users/user/</d:href></d:current-user-principal>",
                    )
                    request.method == "PROPFIND" && "calendar-home-set" in body -> multistatus(
                        "/principals/users/user/", "<c:calendar-home-set><d:href>/calendar/user/</d:href></c:calendar-home-set>",
                    )
                    request.method == "PROPFIND" && request.path == "/calendar/user/" -> MockResponse()
                        .setResponseCode(207)
                        .setBody(
                            """
                            <?xml version="1.0" encoding="utf-8"?>
                            <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                              <d:response><d:href>/calendar/user/</d:href>
                                <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                                <d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
                              <d:response><d:href>/calendar/user/work/</d:href>
                                <d:propstat><d:prop><d:resourcetype><d:collection/><c:calendar/></d:resourcetype>
                                  <d:displayname>업무</d:displayname>
                                  <c:supported-calendar-component-set><c:comp name="VEVENT"/></c:supported-calendar-component-set>
                                </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
                              <d:response><d:href>/calendar/user/todo/</d:href>
                                <d:propstat><d:prop><d:resourcetype><d:collection/><c:calendar/></d:resourcetype>
                                  <d:displayname>할 일</d:displayname>
                                  <c:supported-calendar-component-set><c:comp name="VTODO"/></c:supported-calendar-component-set>
                                </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
                            </d:multistatus>
                            """.trimIndent(),
                        )
                    request.method == "PUT" -> MockResponse().setResponseCode(201)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After fun tearDown() = server.shutdown()

    private fun multistatus(href: String, prop: String) = MockResponse().setResponseCode(207).setBody(
        """
        <?xml version="1.0" encoding="utf-8"?>
        <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
          <d:response><d:href>$href</d:href><d:propstat><d:prop>$prop</d:prop>
          <d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
        </d:multistatus>
        """.trimIndent(),
    )

    @Test fun discoversEventCalendarsOnly() {
        val client = CalDavClient("user", "app-pw", baseUrl = server.url("/").toString())
        val calendars = client.discoverCalendars()
        assertEquals(1, calendars.size)
        assertEquals("업무", calendars[0].displayName)
        assertEquals(server.url("/calendar/user/work/").toString(), calendars[0].url)
    }

    @Test fun createsEventWithPut() {
        val client = CalDavClient("user", "app-pw", baseUrl = server.url("/").toString())
        val url = client.createEvent(server.url("/calendar/user/work").toString(), "uid-1", "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n")
        assertEquals(server.url("/calendar/user/work/uid-1.ics").toString(), url)
        val put = requests.last()
        assertEquals("PUT", put.method)
        assertEquals("*", put.getHeader("If-None-Match"))
        assertTrue(put.getHeader("Content-Type")!!.startsWith("text/calendar"))
    }

    @Test fun wrongPasswordReportsAuthError() {
        val client = CalDavClient("user", "wrong", baseUrl = server.url("/").toString())
        val e = assertFailsWith<CalDavClient.CalDavException> { client.discoverCalendars() }
        assertEquals(401, e.code)
    }
}
