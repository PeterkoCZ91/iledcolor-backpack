package com.batoh.core.conversion

import java.io.IOException
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class HttpAwaitTest {
    @Test fun asynchronousResponseIsDeliveredAndCallerOwnsBody() = runBlocking {
        val call = FakeCall()
        val awaited = async(start = CoroutineStart.UNDISPATCHED) { call.awaitResponse() }
        val (response, body) = response(call)
        assertTrue(call.isExecuted())
        assertFalse(awaited.isCompleted)
        call.respond(response)
        assertSame(response, awaited.await())
        assertFalse(body.closed)
        response.use { assertEquals("GIF data", it.body!!.string()) }
        assertTrue(body.closed)
        assertFalse(call.isCanceled())
    }

    @Test fun networkFailurePropagatesOriginalIOException() = runBlocking {
        val call = FakeCall()
        val awaited = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { call.awaitResponse() }
        }
        val error = IOException("Network unavailable")
        call.fail(error)
        val propagated = awaited.await().exceptionOrNull()
        assertTrue(propagated is IOException)
        assertEquals(error.message, propagated?.message)
        assertFalse(call.isCanceled())
    }

    @Test fun cancellationCancelsCallAndClosesLateResponseBody() = runBlocking {
        val call = FakeCall()
        val awaited = async(start = CoroutineStart.UNDISPATCHED) { call.awaitResponse() }
        awaited.cancelAndJoin()
        assertTrue(call.isCanceled())
        val (response, body) = response(call)
        // Simulate a response callback already in flight when OkHttp cancellation happens.
        call.respond(response)
        assertTrue(body.closed)
        assertTrue(awaited.isCancelled)
    }

    @Test fun cancellationBeforeResponseDeliveryClosesAlreadyResumedBody() = runBlocking {
        val call = FakeCall()
        val awaited = async(start = CoroutineStart.UNDISPATCHED) { call.awaitResponse() }
        val (response, body) = response(call)
        // Callback resumes the continuation, but its dispatcher has not run it yet.
        call.respond(response)
        awaited.cancelAndJoin()
        assertTrue(call.isCanceled())
        assertTrue(body.closed)
        assertTrue(awaited.isCancelled)
    }

    @Test fun failureCallbackAfterCancellationIsIgnored() = runBlocking {
        val call = FakeCall()
        val awaited = async(start = CoroutineStart.UNDISPATCHED) { call.awaitResponse() }
        awaited.cancelAndJoin()
        call.fail(IOException("Canceled socket"))
        assertTrue(call.isCanceled())
        assertTrue(awaited.isCancelled)
    }

    private fun response(call: Call): Pair<Response, TrackingBody> {
        val body = TrackingBody()
        return Response.Builder().request(call.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK").body(body).build() to body
    }

    private class TrackingBody : ResponseBody() {
        var closed = false
        private val tracked = object : ForwardingSource(Buffer().writeUtf8("GIF data")) {
            override fun close() {
                closed = true
                super.close()
            }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = 8
        override fun source(): BufferedSource = tracked
    }

    private class FakeCall : Call {
        private val request = Request.Builder().url("https://example.invalid/gif").build()
        private var callback: Callback? = null
        private var executed = false
        private var canceled = false
        override fun request(): Request = request
        override fun execute(): Response = error("awaitResponse must use enqueue")
        override fun enqueue(responseCallback: Callback) {
            check(!executed)
            executed = true
            callback = responseCallback
        }
        override fun cancel() { canceled = true }
        override fun isExecuted(): Boolean = executed
        override fun isCanceled(): Boolean = canceled
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = FakeCall()
        fun respond(response: Response) { requireNotNull(callback).onResponse(this, response) }
        fun fail(error: IOException) { requireNotNull(callback).onFailure(this, error) }
    }
}
