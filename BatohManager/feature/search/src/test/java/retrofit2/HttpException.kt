package retrofit2

/** Test stand-in: the search module matches Retrofit's exception by class name only. */
class HttpException(private val status: Int) : RuntimeException("HTTP $status") {
    fun code(): Int = status
}
