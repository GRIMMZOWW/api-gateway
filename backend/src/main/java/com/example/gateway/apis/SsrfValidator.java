package com.example.gateway.apis;

import com.example.gateway.common.ApiException;
import com.example.gateway.common.GatewayProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.function.Function;

/** SSRF validator. */
@Component
public class SsrfValidator {

    private final GatewayProperties props;
    private final Function<String, InetAddress[]> hostResolver;

    /** Production constructor used by Spring. */
    @Autowired
    public SsrfValidator(GatewayProperties props) {
        this(props, SsrfValidator::defaultResolve);
    }

    /** Test/override constructor. */
    SsrfValidator(GatewayProperties props, Function<String, InetAddress[]> hostResolver) {
        this.props = props;
        this.hostResolver = hostResolver;
    }

    /** Validates the given URL. */
    public void validate(String url) {
        if (url == null || url.isBlank()) {
            throw blocked();
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException ex) {
            throw blocked();
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw blocked();
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw blocked();
        }

        InetAddress[] addresses;
        try {
            addresses = hostResolver.apply(host);
        } catch (RuntimeException ex) {
            // Includes UnknownHostException wrapped as RuntimeException; treat
            // any resolution failure as a blocked URL (defensive).
            throw blocked();
        }
        if (addresses == null || addresses.length == 0) {
            throw blocked();
        }

        if (!props.allowPrivateUpstreams()) {
            for (InetAddress addr : addresses) {
                if (isPrivate(addr)) {
                    throw blocked();
                }
            }
        }
    }

    /** Returns true if the address is in a private/loopback/link-local/wildcard range. */
    boolean isPrivate(InetAddress addr) {
        // 0.0.0.0 and:: (wildcard/any) — never a valid upstream target.
        if (addr.isAnyLocalAddress()) {
            return true;
        }
        // 127.0.0.0/8 and::1 loopback.
        if (addr.isLoopbackAddress()) {
            return true;
        }
        // 169.254.0.0/16 and fe80::/10 link-local.
        if (addr.isLinkLocalAddress()) {
            return true;
        }
        if (addr instanceof Inet4Address v4) {
            byte[] b = v4.getAddress();
            // 10.0.0.0/8
            if (b[0] == 10) {
                return true;
            }
            // 172.16.0.0/12: first byte 172, second byte in [16, 31].
            if (b[0] == (byte) 172 && (b[1] & 0xFF) >= 16 && (b[1] & 0xFF) <= 31) {
                return true;
            }
            // 192.168.0.0/16
            if (b[0] == (byte) 192 && b[1] == (byte) 168) {
                return true;
            }
        } else if (addr instanceof Inet6Address v6) {
            byte[] b = v6.getAddress();
            // fc00::/7: top 7 bits == 1111110 → matches fc.. and fd..
            if ((b[0] & 0xFE) == (byte) 0xFC) {
                return true;
            }
        }
        return false;
    }

    private static InetAddress[] defaultResolve(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            // Wrap so the Function contract is honoured; validate() rethrows as blocked.
            throw new RuntimeException(e);
        }
    }

    private static ApiException blocked() {
        return new ApiException("SSRF_BLOCKED", "Upstream URL is not allowed.", HttpStatus.BAD_REQUEST);
    }
}
