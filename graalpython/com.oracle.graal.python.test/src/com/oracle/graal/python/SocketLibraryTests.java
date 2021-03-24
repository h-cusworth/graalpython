/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python;

import static com.oracle.graal.python.runtime.PosixConstants.AF_INET;
import static com.oracle.graal.python.runtime.PosixConstants.INADDR_LOOPBACK;
import static com.oracle.graal.python.runtime.PosixConstants.SOCK_DGRAM;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.oracle.graal.python.runtime.SocketLibrary.Inet4SockAddr;
import com.oracle.graal.python.runtime.SocketLibrary.UniversalSockAddr;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.SocketLibrary;

public class SocketLibraryTests {

    private static Map<String, String> getOptions() {
        HashMap<String, String> options = new HashMap<>();
        options.put("python.PosixModuleBackend", "native");
        options.put("python.CAPI", "/home/otethal/graalvm/graalpython/mxbuild/graalpython/com.oracle.graal.python.cext");
        return options;
    }

    @Rule public WithPythonContextRule withPythonContextRule = new WithPythonContextRule(getOptions());

    @Rule public CleanupRule cleanup = new CleanupRule();

    private Object posixSupport;
    private SocketLibrary lib;

    @Before
    public void setUp() {
        posixSupport = withPythonContextRule.getPythonContext().getPosixSupport();
        lib = SocketLibrary.getUncached();
    }

    @Test
    public void fillSockAddrStorage() {
        Inet4SockAddr addr = new Inet4SockAddr(12345, INADDR_LOOPBACK.value);

        UniversalSockAddr storage = sockAddrStorage();
        lib.fillUniversalSockAddr(posixSupport, storage, addr);
        assertEquals(AF_INET.value, lib.getUniversalSockAddrFamily(posixSupport, storage));
        Inet4SockAddr addr2 = lib.universalSockAddrAsInet4SockAddr(posixSupport, storage);
        assertEquals(addr.getPort(), addr2.getPort());
        assertEquals(addr.getAddress(), addr2.getAddress());

        UniversalSockAddr storageCopy = sockAddrStorage();
        lib.fillUniversalSockAddr(posixSupport, storageCopy, storage);
        assertEquals(AF_INET.value, lib.getUniversalSockAddrFamily(posixSupport, storageCopy));
        Inet4SockAddr addr3 = lib.universalSockAddrAsInet4SockAddr(posixSupport, storageCopy);
        assertEquals(addr.getPort(), addr3.getPort());
        assertEquals(addr.getAddress(), addr3.getAddress());
    }

    @Test
    public void bindGetsocknameDirectAddr() throws PosixException {
        int s = socket(AF_INET.value, SOCK_DGRAM.value, 0);
        lib.bind(posixSupport, s, new Inet4SockAddr(0, INADDR_LOOPBACK.value));

        Inet4SockAddr boundAddr = new Inet4SockAddr();
        lib.getsockname(posixSupport, s, boundAddr);
        assertTrue(boundAddr.getPort() != 0);
        assertEquals(INADDR_LOOPBACK.value, boundAddr.getAddress());
    }

    @Test
    public void bindGetsocknameAddrStorage() throws PosixException {
        int s = socket(AF_INET.value, SOCK_DGRAM.value, 0);
        UniversalSockAddr bindAddrStorage = sockAddrStorage();
        lib.fillUniversalSockAddr(posixSupport, bindAddrStorage, new Inet4SockAddr(0, INADDR_LOOPBACK.value));
        lib.bind(posixSupport, s, bindAddrStorage);

        UniversalSockAddr boundAddrStorage = sockAddrStorage();
        lib.getsockname(posixSupport, s, boundAddrStorage);
        assertEquals(AF_INET.value, lib.getUniversalSockAddrFamily(posixSupport, boundAddrStorage));
        Inet4SockAddr boundAddr = lib.universalSockAddrAsInet4SockAddr(posixSupport, boundAddrStorage);
        assertTrue(boundAddr.getPort() != 0);
        assertEquals(INADDR_LOOPBACK.value, boundAddr.getAddress());
    }

    @Test
    public void sendtoRecvfromDirectAddr() throws PosixException {
        int srvSocket = socket(AF_INET.value, SOCK_DGRAM.value, 0);
        lib.bind(posixSupport, srvSocket, new Inet4SockAddr(0, INADDR_LOOPBACK.value));

        Inet4SockAddr srvAddr = new Inet4SockAddr();
        lib.getsockname(posixSupport, srvSocket, srvAddr);

        int cliSocket = socket(AF_INET.value, SOCK_DGRAM.value, 0);
        byte[] data = new byte[]{1, 2, 3};
        int sentCount = lib.sendto(posixSupport, cliSocket, data, data.length, 0, srvAddr);
        assertEquals(data.length, sentCount);

        byte[] buf = new byte[100];
        Inet4SockAddr srcAddr = new Inet4SockAddr();
        int recvCount = lib.recvfrom(posixSupport, srvSocket, buf, buf.length, 0, srcAddr);

        assertEquals(data.length, recvCount);
        assertArrayEquals(data, Arrays.copyOf(buf, recvCount));

        Inet4SockAddr cliAddr = new Inet4SockAddr();
        lib.getsockname(posixSupport, cliSocket, cliAddr);

        assertEquals(INADDR_LOOPBACK.value, srcAddr.getAddress());
        assertEquals(cliAddr.getPort(), srcAddr.getPort());
    }

    @Test
    public void sendtoRecvfromAddrStorage() throws PosixException {
        int srvSocket = socket(AF_INET.value, SOCK_DGRAM.value, 0);
        UniversalSockAddr bindAddrStorage = sockAddrStorage();
        lib.fillUniversalSockAddr(posixSupport, bindAddrStorage, new Inet4SockAddr(0, INADDR_LOOPBACK.value));
        lib.bind(posixSupport, srvSocket, bindAddrStorage);

        UniversalSockAddr srvAddrStorage = sockAddrStorage();
        lib.getsockname(posixSupport, srvSocket, srvAddrStorage);

        int cliSocket = socket(AF_INET.value, SOCK_DGRAM.value, 0);
        byte[] data = new byte[]{1, 2, 3};
        int sentCount = lib.sendto(posixSupport, cliSocket, data, data.length, 0, srvAddrStorage);
        assertEquals(data.length, sentCount);

        byte[] buf = new byte[100];
        UniversalSockAddr srcAddrStorage = sockAddrStorage();
        int recvCount = lib.recvfrom(posixSupport, srvSocket, buf, buf.length, 0, srcAddrStorage);

        assertEquals(data.length, recvCount);
        assertArrayEquals(data, Arrays.copyOf(buf, recvCount));

        assertEquals(AF_INET.value, lib.getUniversalSockAddrFamily(posixSupport, srcAddrStorage));
        Inet4SockAddr srcAddr = lib.universalSockAddrAsInet4SockAddr(posixSupport, srcAddrStorage);

        UniversalSockAddr cliAddrStorage = sockAddrStorage();
        lib.getsockname(posixSupport, cliSocket, cliAddrStorage);
        assertEquals(AF_INET.value, lib.getUniversalSockAddrFamily(posixSupport, cliAddrStorage));
        Inet4SockAddr cliAddr = lib.universalSockAddrAsInet4SockAddr(posixSupport, cliAddrStorage);

        assertEquals(INADDR_LOOPBACK.value, srcAddr.getAddress());
        assertEquals(cliAddr.getPort(), srcAddr.getPort());
    }

    private int socket(int family, int type, int protocol) throws PosixException {
        int sockfd = lib.socket(posixSupport, family, type, protocol);
        cleanup.add(() -> PosixSupportLibrary.getUncached().close(posixSupport, sockfd));
        return sockfd;
    }

    private UniversalSockAddr sockAddrStorage() {
        UniversalSockAddr universalSockAddr = lib.allocUniversalSockAddr(posixSupport);
        cleanup.add(() -> lib.freeUniversalSockAddr(posixSupport, universalSockAddr));
        return universalSockAddr;
    }
}
