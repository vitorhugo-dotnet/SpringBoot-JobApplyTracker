package com.jobtracker.unit.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.mcp.audit.AuditMcpOperation;
import com.jobtracker.mcp.audit.McpAuditAspect;
import com.jobtracker.service.McpAuditService;
import com.jobtracker.service.TokenEstimatorService;
import com.jobtracker.util.SecurityUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test that exercises {@link McpAuditAspect} through a <em>real</em> Spring AOP proxy
 * (not a direct advice call), mirroring how the MCP framework invokes the proxied tool/resource
 * beans in production.
 *
 * <p>This is the path that previously failed with
 * "Required to bind 2 arguments, but only bound 1 (JoinPointMatch was NOT bound in invocation)"
 * when the advice bound the annotation as a second argument via {@code @annotation(x)}. The advice
 * now reads the annotation off the method reflectively, so the pointcut is purely type-based and
 * binds cleanly.
 */
class McpAuditAspectProxyTest {

    private SimpleMeterRegistry meterRegistry;

    private Target proxyFor(Target target) {
        meterRegistry = new SimpleMeterRegistry();
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        when(securityUtils.getCurrentUser()).thenThrow(new RuntimeException("no auth in unit test"));
        McpAuditService auditService =
                new McpAuditService(meterRegistry, new TokenEstimatorService(new ObjectMapper()), securityUtils);

        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);
        when(span.error(any())).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(mock(Tracer.SpanInScope.class));

        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new McpAuditAspect(auditService, tracer));
        return factory.getProxy();
    }

    @Test
    void adviceBindsAndProceedsThroughRealAopProxy() {
        Target proxy = proxyFor(new Target());

        String result = proxy.ping(null);

        assertThat(result).isEqualTo("hello");
        assertThat(meterRegistry.get("mcp.tool.invocations")
                .tag("action", "Ping")
                .tag("status", "SUCCESS")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void adviceRecordsErrorAndRethrowsThroughRealAopProxy() {
        Target proxy = proxyFor(new Target());

        assertThatThrownBy(() -> proxy.fail(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(meterRegistry.get("mcp.tool.invocations")
                .tag("action", "Fail")
                .tag("status", "ERROR")
                .counter().count()).isEqualTo(1.0);
    }

    /** Non-final class with annotated methods so AspectJProxyFactory can CGLIB-proxy it. */
    static class Target {
        @AuditMcpOperation(action = "Ping")
        public String ping(McpSyncServerExchange exchange) {
            return "hello";
        }

        @AuditMcpOperation(action = "Fail")
        public String fail(McpSyncServerExchange exchange) {
            throw new IllegalStateException("boom");
        }
    }
}
