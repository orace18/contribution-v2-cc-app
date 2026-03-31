package com.ccapp.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class HttpRouteLoggingFilterTest {

    private HttpRouteLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new HttpRouteLoggingFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "maxPayloadLength", 4000);
    }

    @Test
    void doFilter_logsSanitizedTraceWithoutCredentialsOrBodies(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/psp/jawupay/webhook");
        request.setRemoteAddr("10.1.1.5");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.addHeader("Authorization", "Bearer secret-token-12345");
        request.addHeader("X-API-KEY", "jawupay-api-key-secret");
        request.setContent("""
                {"provider":"JAWUPAY","amount":150000,"msisdn":"22501234567"}
                """.getBytes());

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.getWriter().write("""
                    {"paymentId":"PAY-123","status":"PENDING"}
                    """);
        };

        filter.doFilter(request, response, chain);

        String logs = output.getOut();
        assertThat(logs).contains("route=/psp/jawupay/webhook");
        assertThat(logs).contains("authorization=present");
        assertThat(logs).contains("xApiKey=present");
        assertThat(logs).contains("requestBytes=");
        assertThat(logs).contains("responseBytes=");
        assertThat(logs).doesNotContain("secret-token-12345");
        assertThat(logs).doesNotContain("jawupay-api-key-secret");
        assertThat(logs).doesNotContain("22501234567");
        assertThat(logs).doesNotContain("\"provider\":\"JAWUPAY\"");
        assertThat(logs).doesNotContain("\"paymentId\":\"PAY-123\"");
    }
}
