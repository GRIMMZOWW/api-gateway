package com.example.gateway.apis;

import com.example.gateway.common.ApiException;
import com.example.gateway.common.GatewayProperties;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit test for {@link SsrfValidator}. Uses a fake {@code hostResolver} so
 * no DNS lookups happen: hostnames are mapped to fixed IP arrays; IP literals
 * in the URL pass through {@link InetAddress#getAllByName(String)} which does
 * <em>not</em> hit the network for literal addresses.
 */
class SsrfValidatorTest {

    private static final Map<String, String[]> HOST_MAP = Map.of(
        "evil.example.com", new String[]{"127.0.0.1"},
        "public.example.com", new String[]{"93.184.216.34"}
    );

    /**
     * Fake resolver: returns canned IPs for known hostnames; for IP-literal
     * inputs (e.g. "10.0.0.1", "::1", "0.0.0.0") it delegates to
     * {@link InetAddress#getAllByName(String)} which parses them without DNS.
     */
    private final Function<String, InetAddress[]> fakeResolver = host -> {
        try {
            String[] ips = HOST_MAP.get(host);
            if (ips != null) {
                InetAddress[] result = new InetAddress[ips.length];
                for (int i = 0; i < ips.length; i++) {
                    result[i] = InetAddress.getByName(ips[i]);
                }
                return result;
            }
            // IP-literal path: getAllByName parses literals without DNS.
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    };

    private SsrfValidator strict() {
        return new SsrfValidator(props(false), fakeResolver);
    }

    private SsrfValidator permissive() {
        return new SsrfValidator(props(true), fakeResolver);
    }

    private static GatewayProperties props(boolean allowPrivateUpstreams) {
        // Only allowPrivateUpstreams matters here; the other fields are defaults.
        return new GatewayProperties(
            1_048_576, 10_000, 60, 10_000,
            10_000, 1_000L, 100, allowPrivateUpstreams);
    }

    @Test
    void rejectsNonHttpScheme() {
        ApiException ex = assertThrows(ApiException.class,
            () -> strict().validate("ftp://evil.example.com/x"));
        assertTrue(ex.code().equals("SSRF_BLOCKED"));
    }

    @Test
    void rejectsHostnameResolvingToLoopback() {
        assertThrows(ApiException.class,
            () -> strict().validate("http://evil.example.com/x"));
    }

    @Test
    void rejectsPrivateIpv4Ranges() {
        SsrfValidator v = strict();
        assertThrows(ApiException.class, () -> v.validate("http://10.0.0.1/x"));
        assertThrows(ApiException.class, () -> v.validate("http://192.168.1.1/x"));
        assertThrows(ApiException.class, () -> v.validate("http://169.254.1.1/x"));
        assertThrows(ApiException.class, () -> v.validate("http://172.16.0.1/x"));
        // 172.32.x should NOT be matched by 172.16/12; sanity check the boundary.
        assertThrows(ApiException.class, () -> v.validate("http://172.31.255.1/x"));
    }

    @Test
    void rejectsIpv6LoopbackAndWildcard() {
        SsrfValidator v = strict();
        assertThrows(ApiException.class, () -> v.validate("http://[::1]/x"));
        assertThrows(ApiException.class, () -> v.validate("http://0.0.0.0/x"));
    }

    @Test
    void rejectsMalformedUrl() {
        assertThrows(ApiException.class, () -> strict().validate("not-a-url"));
        assertThrows(ApiException.class, () -> strict().validate(""));
    }

    @Test
    void acceptsPublicHostname() {
        // Fake resolver returns 93.184.216.34 for public.example.com.
        assertDoesNotThrow(() -> strict().validate("https://public.example.com/v1/users"));
    }

    @Test
    void allowPrivateUpstreamsSkipsPrivateCheck() {
        // With the toggle on, a private-range IP is allowed (local dev only).
        assertDoesNotThrow(() -> permissive().validate("http://10.0.0.1/x"));
        assertDoesNotThrow(() -> permissive().validate("http://[::1]/x"));
    }
}
