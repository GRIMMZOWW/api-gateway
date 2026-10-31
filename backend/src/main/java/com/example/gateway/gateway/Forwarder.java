package com.example.gateway.gateway;

import com.example.gateway.common.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;

/** Forwards a single gateway request to the upstream API using Spring's RestClient. */
@Component
public class Forwarder {

    private final RestClient.Builder restClientBuilder;

    public Forwarder(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    public ForwardResult forward(String method, String targetUrl, byte[] body,
                                 HttpHeaders requestHeaders, int timeoutMs) {
        NoRedirectHttpRequestFactory factory = new NoRedirectHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        RestClient client = restClientBuilder.requestFactory(factory).build();

        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
        RestClient.RequestBodySpec spec = client.method(httpMethod).uri(targetUrl);
        spec.headers(h -> {
            if (requestHeaders != null) {
                requestHeaders.forEach(h::put);
            }
        });
        if (body != null && body.length > 0) {
            spec.body(body);
        }

        try {
            var response = spec.retrieve().toEntity(byte[].class);
            return new ForwardResult(
                response.getStatusCode().value(),
                response.getHeaders(),
                response.getBody());
        } catch (HttpStatusCodeException ex) {
            // Upstream returned 4xx/5xx. Pass status/headers/body through to the client.
            return new ForwardResult(
                ex.getStatusCode().value(),
                ex.getResponseHeaders() != null ? ex.getResponseHeaders() : new HttpHeaders(),
                ex.getResponseBodyAsByteArray());
        } catch (ResourceAccessException ex) {
            if (ex.getCause() instanceof SocketTimeoutException) {
                throw new ApiException("UPSTREAM_TIMEOUT",
                    "Upstream did not respond within the configured timeout.",
                    org.springframework.http.HttpStatus.GATEWAY_TIMEOUT);
            }
            throw new ApiException("UPSTREAM_ERROR",
                "Could not connect to the upstream API.",
                org.springframework.http.HttpStatus.BAD_GATEWAY);
        } catch (RestClientException ex) {
            throw new ApiException("UPSTREAM_ERROR",
                "Upstream request failed.",
                org.springframework.http.HttpStatus.BAD_GATEWAY);
        }
    }

    /** Does not follow redirects; they are returned to the client as they are. */
    static final class NoRedirectHttpRequestFactory extends SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
        }
    }
}
