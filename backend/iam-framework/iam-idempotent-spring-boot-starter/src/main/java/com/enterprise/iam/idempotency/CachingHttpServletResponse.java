package com.enterprise.iam.idempotency;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Tee 响应：正常透传下游输出，同时捕获 2xx 响应体用于幂等重放。
 */
final class CachingHttpServletResponse extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream capture = new ByteArrayOutputStream();
    private ServletOutputStream outputStream;
    private PrintWriter writer;

    CachingHttpServletResponse(HttpServletResponse response) {
        super(response);
    }

    private Charset charset() {
        String encoding = getCharacterEncoding();
        return encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (outputStream == null) {
            ServletOutputStream downstream = super.getOutputStream();
            outputStream = new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return downstream.isReady();
                }

                @Override
                public void setWriteListener(WriteListener writeListener) {
                    downstream.setWriteListener(writeListener);
                }

                @Override
                public void write(int b) throws IOException {
                    downstream.write(b);
                    capture.write(b);
                }
            };
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), charset()), true);
        }
        return writer;
    }

    CachedHttpResponse snapshot() {
        if (writer != null) {
            writer.flush();
        }
        return new CachedHttpResponse(getStatus(), getContentType(), capture.toByteArray());
    }
}
