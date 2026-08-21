package com.nexus.market.quota;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link TenantQuota} records.
 *
 * <p>All queries target the {@code tenant_quotas} table. Row-Level Security on the
 * underlying PostgreSQL table ensures multi-tenant data isolation at the DB level.
 */
@Repository
public interface TenantQuotaRepository extends JpaRepository<TenantQuota, String> {

    /**
     * Fetch the quota record for a specific tenant.
     *
     * @param tenantId the X-Tenant-ID header value
     * @return an Optional wrapping the tenant's quota, or empty if the tenant is unknown
     */
    Optional<TenantQuota> findByTenantId(String tenantId);

    /**
     * Finds all tenants that currently have unused token surplus (potential lenders).
     * Used by the Stackelberg leader and VCG auction to compute the available pool size.
     *
     * <p>A tenant is a "surplus" tenant when {@code used_tokens < daily_limit}.
     *
     * @return list of quota records with {@code usedTokens < dailyLimit}
     */
    @Query("SELECT q FROM TenantQuota q WHERE q.usedTokens < q.dailyLimit")
    List<TenantQuota> findTenantsWithSurplus();

    /**
     * Computes the aggregate remaining tokens across all tenants — used as the
     * global pool size available for auction allocation.
     *
     * @return sum of (dailyLimit - usedTokens) for all tenants, or 0 if none
     */
    @Query("SELECT COALESCE(SUM(q.dailyLimit - q.usedTokens), 0) FROM TenantQuota q WHERE q.usedTokens < q.dailyLimit")
    long computeTotalSurplusTokens();
}
