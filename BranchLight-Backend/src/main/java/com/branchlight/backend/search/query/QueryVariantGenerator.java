package com.branchlight.backend.search.query;

import java.util.List;

public interface QueryVariantGenerator {

    List<GeneratedQuery> generate(String originalQuery);
}
