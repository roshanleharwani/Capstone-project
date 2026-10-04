import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: 'standalone',
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${process.env.NEXT_PUBLIC_PROXY_URL || (process.env.NODE_ENV === 'development' ? 'http://127.0.0.1:8081' : 'http://nexus-proxy:8080')}/api/:path*` // Proxy to Spring Boot Backend
      }
    ];
  }
};

export default nextConfig;
