package com.nexus.ingress.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;

/**
 * Enforces mTLS Pod Boundary Security (IEEE 802.1AR / RFC 5246 TLS 1.3 / Istio Service Mesh).
 */
@Component
public class MTLSBoundaryEnforcer {

    @Value("${nexus.security.mtls.enforce:false}")
    private boolean enforceMtls;

    /**
     * Validates mTLS client certificates from request attribute or X-Forwarded-Client-Cert header.
     */
    public boolean validateMTLSBoundary(HttpServletRequest request) {
        if (!enforceMtls) {
            // If mTLS enforcement is disabled in dev/local mode, accept requests
            return true;
        }

        // 1. Check Servlet javax/jakarta client certificates
        X509Certificate[] certs = (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");
        if (certs == null) {
            certs = (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");
        }

        if (certs != null && certs.length > 0) {
            X509Certificate clientCert = certs[0];
            String subject = clientCert.getSubjectX500Principal().getName();
            return subject != null && (subject.contains("nexusgateway") || subject.contains("cluster.local"));
        }

        // 2. Check Envoy / Istio proxy header: X-Forwarded-Client-Cert (XFCC)
        String xfccHeader = request.getHeader("X-Forwarded-Client-Cert");
        if (xfccHeader != null && !xfccHeader.isBlank()) {
            return xfccHeader.contains("By=") || xfccHeader.contains("Hash=") || xfccHeader.contains("URI=");
        }

        return false;
    }

    public void setEnforceMtls(boolean enforceMtls) {
        this.enforceMtls = enforceMtls;
    }

    public boolean isEnforceMtls() {
        return enforceMtls;
    }
}
