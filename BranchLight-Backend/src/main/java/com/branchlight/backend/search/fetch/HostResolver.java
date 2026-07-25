package com.branchlight.backend.search.fetch;

import java.net.InetAddress;
import java.net.UnknownHostException;

@FunctionalInterface
interface HostResolver {

    InetAddress[] resolve(String host) throws UnknownHostException;
}
