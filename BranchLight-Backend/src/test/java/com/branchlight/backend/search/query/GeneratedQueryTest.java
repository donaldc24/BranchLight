package com.branchlight.backend.search.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneratedQueryTest {

    @Test
    void exposesEverySupportedQueryPurpose() {
        assertArrayEquals(
                new QueryPurpose[]{
                        QueryPurpose.AUTHORITATIVE,
                        QueryPurpose.EXPLANATORY,
                        QueryPurpose.PRACTICAL,
                        QueryPurpose.CRITICAL,
                        QueryPurpose.HUMAN_DISCUSSION
                },
                QueryPurpose.values());
    }

    @Test
    void retainsTheQueryTextAndPurpose() {
        var generatedQuery = new GeneratedQuery(
                "  virtual threads documentation  ",
                QueryPurpose.AUTHORITATIVE);

        assertAll(
                () -> assertEquals(
                        "  virtual threads documentation  ",
                        generatedQuery.queryText()),
                () -> assertEquals(
                        QueryPurpose.AUTHORITATIVE,
                        generatedQuery.purpose()));
    }

    @Test
    void rejectsNullQueryText() {
        var exception = assertThrows(
                NullPointerException.class,
                () -> new GeneratedQuery(null, QueryPurpose.EXPLANATORY));

        assertEquals("queryText must not be null", exception.getMessage());
    }

    @Test
    void rejectsBlankQueryText() {
        for (String queryText : new String[]{"", " ", "\t\r\n"}) {
            var exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new GeneratedQuery(
                            queryText,
                            QueryPurpose.PRACTICAL));

            assertEquals("queryText must not be blank", exception.getMessage());
        }
    }

    @Test
    void rejectsNullPurpose() {
        var exception = assertThrows(
                NullPointerException.class,
                () -> new GeneratedQuery("virtual threads", null));

        assertEquals("purpose must not be null", exception.getMessage());
    }
}
