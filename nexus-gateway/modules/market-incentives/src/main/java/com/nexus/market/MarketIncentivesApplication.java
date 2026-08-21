package com.nexus.market;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the NexusMarket Incentive Engine module.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Tenant Quota Tracking — intercepts X-Tenant-ID and verifies daily token balances</li>
 *   <li>Stackelberg Dynamic Pricing — calculates token borrowing rates from system-wide load</li>
 *   <li>VCG Second-Price Auction — allocates pooled excess tokens to quota-starved tenants</li>
 *   <li>Credit Ledger — immutable double-entry ledger backed by PostgreSQL + Valkey</li>
 * </ul>
 */
@SpringBootApplication
public class MarketIncentivesApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketIncentivesApplication.class, args);
    }
}
