package com.branchlight.backend.search.fetch;

import java.net.UnknownHostException;

final class BlockedDestinationException
        extends UnknownHostException {

    BlockedDestinationException() {
        super("Destination is not a public network address");
    }
}
