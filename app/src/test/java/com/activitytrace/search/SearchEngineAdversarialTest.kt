package com.activitytrace.search

import com.activitytrace.store.CaptureDao
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchEngineAdversarialTest {

    private val captureDao: CaptureDao = mockk(relaxed = true)
    private lateinit var searchEngine: SearchEngine

    @Before
    fun setUp() {
        every { captureDao.searchFtsCandidates(any(), any(), any(), any(), any()) } returns flowOf(emptyList())
        every { captureDao.searchFtsRecentCandidates(any(), any(), any(), any(), any()) } returns flowOf(emptyList())
        searchEngine = SearchEngine(captureDao)
    }

    /**
     * Every FTS MATCH query produced by the engine must be safe under the FTS5
     * grammar: each token is either a bare alphanumeric word (never an FTS5
     * operator keyword) or a fully double-quoted literal.
     */
    private fun assertMatchQueryIsOperatorSafe(matchQuery: String) {
        val tokens = matchQuery.split(" ").filter { it.isNotEmpty() }
        for (token in tokens) {
            val isQuoted = token.startsWith("\"") && token.endsWith("\"")
            if (isQuoted) continue
            val isBareWord = token.all { it.isLetterOrDigit() || it == '_' }
            assertTrue("quoted or bare word expected, got: $token", isBareWord)
            assertTrue(
                "FTS5 operator keyword must not appear bare: $token",
                token.lowercase() !in setOf("and", "or", "not", "near"),
            )
        }
    }

    @Test
    fun `adversarial syntax inputs never throw and never emit bare operators`() = runTest {
        val inputs = listOf(
            "\"", "*", ":", "(", ")", "AND", "OR", "NOT", "NEAR",
            "foo*", "\"foo bar\"", "foo OR *", "foo\" OR *", "foo)",
            "or", "and", "not", "near",
            "; DROP TABLE captured_items; --",
            "NEAR(1) :", "(", "\"\"\"", "-", "+", "~", "^^", "!",
        )

        for (input in inputs) {
            searchEngine.search(input).collect { }
        }

        val queries = mutableListOf<String>()
        verify(atLeast = 1) { captureDao.searchFtsCandidates(capture(queries), null, null, null) }
        for (q in queries) {
            assertMatchQueryIsOperatorSafe(q)
        }
    }

    @Test
    fun `long query is truncated to parser budget`() = runTest {
        val longQuery = "hello " + "x".repeat(10_000)

        searchEngine.search(longQuery).collect { }

        val queries = mutableListOf<String>()
        verify { captureDao.searchFtsCandidates(capture(queries), null, null, null) }
        val matchQuery = queries.single()
        assertTrue(
            "match query length ${matchQuery.length} exceeds budget",
            matchQuery.length <= QueryParser.MAX_QUERY_LENGTH,
        )
        assertMatchQueryIsOperatorSafe(matchQuery)
    }

    @Test
    fun `query with more terms than the budget keeps only the first terms`() = runTest {
        val manyTerms = (1..200).joinToString(" ")

        searchEngine.search(manyTerms).collect { }

        val queries = mutableListOf<String>()
        verify { captureDao.searchFtsCandidates(capture(queries), null, null, null) }
        val tokenCount = queries.single().split(" ").filter { it.isNotBlank() }.size
        assertTrue("expected <= ${QueryParser.MAX_TERMS} terms, got $tokenCount", tokenCount <= QueryParser.MAX_TERMS)
        assertMatchQueryIsOperatorSafe(queries.single())
    }

    @Test
    fun `bare operator words are quoted as literals`() = runTest {
        searchEngine.search("or and not near").collect { }

        val queries = mutableListOf<String>()
        verify { captureDao.searchFtsCandidates(capture(queries), null, null, null) }
        val matchQuery = queries.single()
        assertMatchQueryIsOperatorSafe(matchQuery)
        assertTrue(matchQuery.contains("\"or\" \"and\" \"not\" \"near\""))
    }

    @Test
    fun `mixed operator and normal words are safe`() = runTest {
        searchEngine.search("hello OR * \"quoted\" (paren)").collect { }

        val queries = mutableListOf<String>()
        verify { captureDao.searchFtsCandidates(capture(queries), null, null, null) }
        assertMatchQueryIsOperatorSafe(queries.single())
    }

    @Test
    fun `quotes inside a token are escaped`() = runTest {
        searchEngine.search("foo\"bar").collect { }

        val queries = mutableListOf<String>()
        verify { captureDao.searchFtsCandidates(capture(queries), null, null, null) }
        assertMatchQueryIsOperatorSafe(queries.single())
        assertTrue(queries.single().contains("\"foo\"\"bar\""))
    }
}