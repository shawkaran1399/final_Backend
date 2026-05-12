package com.buildledger.delivery.dto.response;

import lombok.*;
import java.math.BigDecimal;

/**
 * Contract-level budget summary returned by GET /deliveries/contract/{id}/budget-summary
 *
 * contractValue  = contract.value (from contract-service via Feign)
 * spent          = sum of all delivery prices + service prices for this contract
 * remaining      = contractValue - spent
 * overBudget     = remaining < 0
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ContractBudgetSummaryDTO {
    private Long       contractId;
    private BigDecimal contractValue;   // total contract value
    private BigDecimal spent;           // sum of all delivery + service prices
    private BigDecimal remaining;       // contractValue - spent
    private boolean    overBudget;      // true if spent > contractValue
    private int        deliveryCount;   // number of deliveries under this contract
    private int        serviceCount;    // number of services under this contract
}