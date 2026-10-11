package com.nexus.market.quota;

/** Tokens taken from a surplus tenant during an auction settlement. */
public record LenderContribution(String lenderTenantId, long tokensLent) {}
