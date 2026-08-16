package com.enterprise.iam.gateway.security.jwks;

import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** JDK HTTP client with no ambient proxy, no redirect and default JVM trust. */
public final class JavaHttpClientJwksTransport implements JwksHttpTransport {

    private static final ProxySelector NO_PROXY = new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, java.net.SocketAddress address, IOException error) {
            // HttpClient reports the original failure to the caller.
        }
    };

    private final HttpClient httpClient;

    public JavaHttpClientJwksTransport(Duration connectTimeout) {
        SSLParameters tls = new SSLParameters();
        tls.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        tls.setEndpointIdentificationAlgorithm("HTTPS");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(NO_PROXY)
                .sslParameters(tls)
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    @Override
    public JwksHttpResponse get(URI uri, Duration requestTimeout) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/jwk-set+json, application/json")
                .header("User-Agent", "enterprise-iam-gateway-jwks/1")
                .GET()
                .build();
        try {
            HttpResponse<java.io.InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            return new JwksHttpResponse(
                    response.statusCode(), response.headers().map(), response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new JwksTransportException("JWKS request was interrupted", exception);
        } catch (IOException | RuntimeException exception) {
            throw new JwksTransportException("JWKS request failed", exception);
        }
    }
}
