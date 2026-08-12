package com.nexus.ingress.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class MTLSBoundaryEnforcerTest {

    private MTLSBoundaryEnforcer mtlsEnforcer;

    @BeforeEach
    void setUp() {
        mtlsEnforcer = new MTLSBoundaryEnforcer();
    }

    @Test
    @DisplayName("Should pass when mTLS enforcement is disabled (dev mode)")
    void testDisabledMTLS() {
        mtlsEnforcer.setEnforceMtls(false);
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertTrue(mtlsEnforcer.validateMTLSBoundary(request));
    }

    @Test
    @DisplayName("Should pass when valid X-Forwarded-Client-Cert header is present")
    void testXFCCHeaderPresent() {
        mtlsEnforcer.setEnforceMtls(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Client-Cert", "By=spiffe://cluster.local/ns/default/sa/nexusgateway;Hash=12345");

        assertTrue(mtlsEnforcer.validateMTLSBoundary(request));
    }

    @Test
    @DisplayName("Should reject when mTLS is enforced and no client cert / header is present")
    void testMissingCertRejection() {
        mtlsEnforcer.setEnforceMtls(true);
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertFalse(mtlsEnforcer.validateMTLSBoundary(request));
    }
}
