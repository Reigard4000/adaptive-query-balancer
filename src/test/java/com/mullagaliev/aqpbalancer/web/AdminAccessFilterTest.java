package com.mullagaliev.aqpbalancer.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminAccessFilterTest {

    @Test
    void allowsLoopbackWithoutToken() throws Exception {
        AdminAccessFilter filter = new AdminAccessFilter("", true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/monitor/reset");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void deniesNonLoopbackWithoutToken() throws Exception {
        AdminAccessFilter filter = new AdminAccessFilter("", true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/monitor/reset");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void requiresConfiguredToken() throws Exception {
        AdminAccessFilter filter = new AdminAccessFilter("secret", true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/servers");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Admin-Token", "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void leavesClientEndpointOpen() throws Exception {
        AdminAccessFilter filter = new AdminAccessFilter("", false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/execute");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
    }
}
