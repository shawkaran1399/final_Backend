package com.buildledger.contract.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetSummaryDTO {
    private Long       projectId;
    private String     projectName;
    private BigDecimal totalBudget;
    private BigDecimal spent;
    private BigDecimal remaining;
    private boolean    overBudget;
    private int        activeContractCount;
}
