package com.buildledger.report.service;

import com.buildledger.report.feign.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ProjectServiceClient projectServiceClient;
    private final ContractServiceClient contractServiceClient;
    private final FinanceServiceClient financeServiceClient;
    private final DeliveryServiceClient deliveryServiceClient;
    private final VendorServiceClient vendorServiceClient;
    private final ComplianceServiceClient complianceServiceClient;

    // ─── Existing raw-data report methods ────────────────────────────────────

    public Map<String, Object> generateProjectReport() {
        log.info("Generating project report");
        Map<String, Object> report = new LinkedHashMap<>();
        try {
            Map<String, Object> projects = projectServiceClient.getAllProjects();
            report.put("projects", projects.getOrDefault("data", Collections.emptyList()));
            report.put("success", true);
            report.put("message", "Project report generated successfully");
        } catch (Exception e) {
            log.error("Error generating project report: {}", e.getMessage());
            report.put("success", false);
            report.put("message", "Project Service is currently unavailable");
        }
        return report;
    }

    public Map<String, Object> generateContractReport() {
        log.info("Generating contract report");
        Map<String, Object> report = new LinkedHashMap<>();
        try {
            Map<String, Object> contracts = contractServiceClient.getAllContracts();
            report.put("contracts", contracts.getOrDefault("data", Collections.emptyList()));
            report.put("success", true);
            report.put("message", "Contract report generated successfully");
        } catch (Exception e) {
            log.error("Error generating contract report: {}", e.getMessage());
            report.put("success", false);
            report.put("message", "Contract Service is currently unavailable");
        }
        return report;
    }

    public Map<String, Object> generateFinancialReport() {
        log.info("Generating financial report");
        Map<String, Object> report = new LinkedHashMap<>();
        try {
            Map<String, Object> invoices = financeServiceClient.getAllInvoices();
            Map<String, Object> payments = financeServiceClient.getAllPayments();
            report.put("invoices", invoices.getOrDefault("data", Collections.emptyList()));
            report.put("payments", payments.getOrDefault("data", Collections.emptyList()));
            report.put("success", true);
            report.put("message", "Financial report generated successfully");
        } catch (Exception e) {
            log.error("Error generating financial report: {}", e.getMessage());
            report.put("success", false);
            report.put("message", "Finance Service is currently unavailable");
        }
        return report;
    }

    public Map<String, Object> generateDeliveryReport() {
        log.info("Generating delivery report");
        Map<String, Object> report = new LinkedHashMap<>();
        try {
            Map<String, Object> deliveries = deliveryServiceClient.getAllDeliveries();
            report.put("deliveries", deliveries.getOrDefault("data", Collections.emptyList()));
            report.put("success", true);
            report.put("message", "Delivery report generated successfully");
        } catch (Exception e) {
            log.error("Error generating delivery report: {}", e.getMessage());
            report.put("success", false);
            report.put("message", "Delivery Service is currently unavailable");
        }
        return report;
    }

    // ─── New page-level summary methods ──────────────────────────────────────

    public Map<String, Object> generateDashboardSummary() {
        log.info("Generating dashboard summary");
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> contracts  = extractList(contractServiceClient.getAllContracts());
            List<Map<String, Object>> vendors    = extractList(vendorServiceClient.getAllVendors());
            List<Map<String, Object>> deliveries = extractList(deliveryServiceClient.getAllDeliveries());
            List<Map<String, Object>> invoices   = extractList(financeServiceClient.getAllInvoices());

            // KPI counts
            long totalContracts     = contracts.size();
            long activeVendors      = vendors.stream().filter(v -> "ACTIVE".equals(v.get("status"))).count();
            long pendingDeliveries  = deliveries.stream().filter(d -> "PENDING".equals(d.get("status"))).count();
            double outstandingPayments = invoices.stream()
                    .filter(i -> !"PAID".equals(i.get("status")))
                    .mapToDouble(i -> toDouble(i.getOrDefault("amount", 0)))
                    .sum();

            // Monthly contract value trend (Jan–Dec)
            String[] monthNames = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
            double[] monthTotals = new double[12];
            for (Map<String, Object> c : contracts) {
                String dateStr = firstNonNull(c.get("createdAt"), c.get("startDate"));
                if (dateStr == null) continue;
                int month = parseMonthIndex(dateStr);
                if (month >= 0) {
                    monthTotals[month] += toDouble(c.getOrDefault("value", c.getOrDefault("contractValue", 0)));
                }
            }
            List<Map<String, Object>> contractTrendData = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("month", monthNames[i]);
                entry.put("value", monthTotals[i]);
                contractTrendData.add(entry);
            }

            // Vendor status snapshot (top 6)
            List<Map<String, Object>> vendorStatusData = new ArrayList<>();
            for (Map<String, Object> v : vendors.subList(0, Math.min(6, vendors.size()))) {
                String name   = String.valueOf(v.getOrDefault("name", "Vendor"));
                String status = String.valueOf(v.getOrDefault("status", ""));
                int score = "ACTIVE".equals(status) ? 85 : "PENDING".equals(status) ? 50 : 30;
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name",  name.length() > 10 ? name.substring(0, 10) : name);
                entry.put("score", score);
                vendorStatusData.add(entry);
            }

            // Recent 6 contracts
            List<Map<String, Object>> recentContracts =
                    contracts.subList(0, Math.min(6, contracts.size()));

            // Alerts: overdue invoices + pending deliveries (max 4)
            List<Map<String, Object>> alerts = new ArrayList<>();
            LocalDate today = LocalDate.now();
            for (Map<String, Object> inv : invoices) {
                if (alerts.size() >= 4) break;
                String status  = String.valueOf(inv.getOrDefault("status", ""));
                String dueDateStr = String.valueOf(inv.getOrDefault("dueDate", ""));
                if ("UNDER_REVIEW".equals(status) && !dueDateStr.isEmpty() && !"null".equals(dueDateStr)) {
                    try {
                        LocalDate due = LocalDate.parse(dueDateStr.substring(0, 10));
                        if (due.isBefore(today)) {
                            Map<String, Object> alert = new LinkedHashMap<>();
                            alert.put("id",       "inv-" + inv.get("invoiceId"));
                            alert.put("severity", "error");
                            alert.put("message",  "Invoice #" + inv.get("invoiceId") + " is overdue");
                            alert.put("time",     dueDateStr);
                            alerts.add(alert);
                        }
                    } catch (DateTimeParseException ignored) {}
                }
            }
            for (Map<String, Object> del : deliveries) {
                if (alerts.size() >= 4) break;
                if ("PENDING".equals(del.get("status"))) {
                    Map<String, Object> alert = new LinkedHashMap<>();
                    alert.put("id",       "del-" + del.get("deliveryId"));
                    alert.put("severity", "warning");
                    alert.put("message",  "Delivery #" + del.get("deliveryId") + " is pending");
                    alert.put("time",     firstNonNull(del.get("scheduledDate"), del.get("expectedDate"), "—"));
                    alerts.add(alert);
                }
            }

            result.put("totalContracts",      totalContracts);
            result.put("activeVendors",        activeVendors);
            result.put("pendingDeliveries",    pendingDeliveries);
            result.put("outstandingPayments",  outstandingPayments);
            result.put("contractTrendData",    contractTrendData);
            result.put("vendorStatusData",     vendorStatusData);
            result.put("recentContracts",      recentContracts);
            result.put("alerts",               alerts);
            result.put("success", true);
            result.put("message", "Dashboard summary generated successfully");
        } catch (Exception e) {
            log.error("Error generating dashboard summary: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Unable to generate dashboard summary: " + e.getMessage());
        }
        return result;
    }

    public Map<String, Object> generateContractPageSummary() {
        log.info("Generating contract page summary");
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> contracts = extractList(contractServiceClient.getAllContracts());

            Map<String, Long> statusCounts = new LinkedHashMap<>();
            statusCounts.put("ALL",        (long) contracts.size());
            statusCounts.put("DRAFT",      countByStatus(contracts, "DRAFT"));
            statusCounts.put("ACTIVE",     countByStatus(contracts, "ACTIVE"));
            statusCounts.put("COMPLETED",  countByStatus(contracts, "COMPLETED"));
            statusCounts.put("TERMINATED", countByStatus(contracts, "TERMINATED"));
            statusCounts.put("EXPIRED",    countByStatus(contracts, "EXPIRED"));

            result.put("statusCounts", statusCounts);
            result.put("success", true);
            result.put("message", "Contract page summary generated successfully");
        } catch (Exception e) {
            log.error("Error generating contract page summary: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Contract Service is currently unavailable");
        }
        return result;
    }

    public Map<String, Object> generateInvoicePageSummary() {
        log.info("Generating invoice page summary");
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> invoices = extractList(financeServiceClient.getAllInvoices());
            List<Map<String, Object>> payments = extractList(financeServiceClient.getAllPayments());

            LocalDate today = LocalDate.now();

            double totalInvoiced = invoices.stream()
                    .mapToDouble(i -> toDouble(i.getOrDefault("amount", 0))).sum();
            double paid = invoices.stream()
                    .filter(i -> "PAID".equals(i.get("status")))
                    .mapToDouble(i -> toDouble(i.getOrDefault("amount", 0))).sum();
            double underReview = invoices.stream()
                    .filter(i -> "UNDER_REVIEW".equals(i.get("status")))
                    .mapToDouble(i -> toDouble(i.getOrDefault("amount", 0))).sum();
            double overdue = invoices.stream()
                    .filter(i -> {
                        if (!"UNDER_REVIEW".equals(i.get("status"))) return false;
                        String due = String.valueOf(i.getOrDefault("dueDate", ""));
                        if (due.isEmpty() || "null".equals(due)) return false;
                        try {
                            return LocalDate.parse(due.substring(0, 10)).isBefore(today);
                        } catch (DateTimeParseException e) { return false; }
                    })
                    .mapToDouble(i -> toDouble(i.getOrDefault("amount", 0))).sum();

            // Monthly payment trend: paid vs pending
            String[] monthNames = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
            double[] paidByMonth    = new double[12];
            double[] pendingByMonth = new double[12];
            for (Map<String, Object> p : payments) {
                String dateStr = String.valueOf(p.getOrDefault("date", p.getOrDefault("createdAt", "")));
                if (dateStr.isEmpty() || "null".equals(dateStr)) continue;
                int month = parseMonthIndex(dateStr);
                if (month < 0) continue;
                double amt = toDouble(p.getOrDefault("amount", 0));
                String pStatus = String.valueOf(p.getOrDefault("status", ""));
                if ("COMPLETED".equals(pStatus)) paidByMonth[month]    += amt;
                else                              pendingByMonth[month] += amt;
            }
            List<Map<String, Object>> paymentTrendData = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("month",   monthNames[i]);
                entry.put("paid",    paidByMonth[i]);
                entry.put("pending", pendingByMonth[i]);
                paymentTrendData.add(entry);
            }

            result.put("totalInvoiced",    totalInvoiced);
            result.put("paid",             paid);
            result.put("underReview",      underReview);
            result.put("overdue",          overdue);
            result.put("paymentTrendData", paymentTrendData);
            result.put("success", true);
            result.put("message", "Invoice page summary generated successfully");
        } catch (Exception e) {
            log.error("Error generating invoice page summary: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Finance Service is currently unavailable");
        }
        return result;
    }

    public Map<String, Object> generateVendorPageSummary() {
        log.info("Generating vendor page summary");
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> vendors = extractList(vendorServiceClient.getAllVendors());
            List<Map<String, Object>> pending = extractList(vendorServiceClient.getPendingDocuments());

            Map<String, Long> statusCounts = new LinkedHashMap<>();
            statusCounts.put("ACTIVE",    countByStatus(vendors, "ACTIVE"));
            statusCounts.put("PENDING",   countByStatus(vendors, "PENDING"));
            statusCounts.put("REJECTED",  countByStatus(vendors, "REJECTED"));
            statusCounts.put("SUSPENDED", countByStatus(vendors, "SUSPENDED"));

            result.put("totalVendors",          (long) vendors.size());
            result.put("statusCounts",           statusCounts);
            result.put("pendingDocumentsCount",  (long) pending.size());
            result.put("success", true);
            result.put("message", "Vendor page summary generated successfully");
        } catch (Exception e) {
            log.error("Error generating vendor page summary: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Vendor Service is currently unavailable");
        }
        return result;
    }

    public Map<String, Object> generateProjectPageSummary() {
        log.info("Generating project page summary");
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> projects = extractList(projectServiceClient.getAllProjects());

            Map<String, Long> statusCounts = new LinkedHashMap<>();
            statusCounts.put("ALL",       (long) projects.size());
            statusCounts.put("PLANNING",  countByStatus(projects, "PLANNING"));
            statusCounts.put("ACTIVE",    countByStatus(projects, "ACTIVE"));
            statusCounts.put("ON_HOLD",   countByStatus(projects, "ON_HOLD"));
            statusCounts.put("COMPLETED", countByStatus(projects, "COMPLETED"));
            statusCounts.put("CANCELLED", countByStatus(projects, "CANCELLED"));

            result.put("statusCounts", statusCounts);
            result.put("success", true);
            result.put("message", "Project page summary generated successfully");
        } catch (Exception e) {
            log.error("Error generating project page summary: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Project Service is currently unavailable");
        }
        return result;
    }

    public Map<String, Object> generateDeliveryPageSummary() {
        log.info("Generating delivery page summary");
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> deliveries = extractList(deliveryServiceClient.getAllDeliveries());
            List<Map<String, Object>> services   = extractList(deliveryServiceClient.getAllServices());

            Map<String, Long> deliveryStatusCounts = new LinkedHashMap<>();
            deliveryStatusCounts.put("PENDING",          countByStatus(deliveries, "PENDING"));
            deliveryStatusCounts.put("MARKED_DELIVERED", countByStatus(deliveries, "MARKED_DELIVERED"));
            deliveryStatusCounts.put("DELAYED",          countByStatus(deliveries, "DELAYED"));
            deliveryStatusCounts.put("ACCEPTED",         countByStatus(deliveries, "ACCEPTED"));
            deliveryStatusCounts.put("REJECTED",         countByStatus(deliveries, "REJECTED"));

            Map<String, Long> serviceStatusCounts = new LinkedHashMap<>();
            serviceStatusCounts.put("PENDING",     countByStatus(services, "PENDING"));
            serviceStatusCounts.put("IN_PROGRESS", countByStatus(services, "IN_PROGRESS"));
            serviceStatusCounts.put("COMPLETED",   countByStatus(services, "COMPLETED"));
            serviceStatusCounts.put("VERIFIED",    countByStatus(services, "VERIFIED"));

            result.put("deliveryStatusCounts", deliveryStatusCounts);
            result.put("serviceStatusCounts",  serviceStatusCounts);
            result.put("success", true);
            result.put("message", "Delivery page summary generated successfully");
        } catch (Exception e) {
            log.error("Error generating delivery page summary: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Delivery Service is currently unavailable");
        }
        return result;
    }

    public Map<String, Object> generateCompliancePageSummary() {
        log.info("Generating compliance page summary");
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> complianceItems = extractList(complianceServiceClient.getAllCompliance());

            long compliant    = complianceItems.stream()
                    .filter(c -> "PASSED".equals(c.get("status")) || "WAIVED".equals(c.get("status"))).count();
            long nonCompliant = countByStatus(complianceItems, "FAILED");
            long pending      = complianceItems.stream()
                    .filter(c -> "PENDING".equals(c.get("status")) || "UNDER_REVIEW".equals(c.get("status"))).count();
            long total        = complianceItems.size();
            int overallScore  = total > 0 ? (int) Math.round((compliant * 100.0) / total) : 0;

            List<Map<String, Object>> pieChartData = new ArrayList<>();
            pieChartData.add(Map.of("name", "Compliant",    "value", compliant));
            pieChartData.add(Map.of("name", "Pending",      "value", pending));
            pieChartData.add(Map.of("name", "Non-Compliant","value", nonCompliant));

            result.put("compliant",     compliant);
            result.put("nonCompliant",  nonCompliant);
            result.put("pending",       pending);
            result.put("total",         total);
            result.put("overallScore",  overallScore);
            result.put("pieChartData",  pieChartData);
            result.put("success", true);
            result.put("message", "Compliance page summary generated successfully");
        } catch (Exception e) {
            log.error("Error generating compliance page summary: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Compliance Service is currently unavailable");
        }
        return result;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractList(Map<String, Object> response) {
        Object data = response.getOrDefault("data", Collections.emptyList());
        if (data instanceof List) return (List<Map<String, Object>>) data;
        return Collections.emptyList();
    }

    private long countByStatus(List<Map<String, Object>> items, String status) {
        return items.stream().filter(item -> status.equals(item.get("status"))).count();
    }

    private double toDouble(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        return 0.0;
    }

    /** Returns the string value of the first non-null argument. */
    private String firstNonNull(Object... values) {
        for (Object v : values) {
            if (v != null && !"null".equals(String.valueOf(v)) && !String.valueOf(v).isEmpty()) {
                return String.valueOf(v);
            }
        }
        return "—";
    }

    /**
     * Parses an ISO date/datetime string and returns 0-based month index (0=Jan … 11=Dec).
     * Returns -1 on failure.
     */
    private int parseMonthIndex(String dateStr) {
        if (dateStr == null || dateStr.isEmpty() || "null".equals(dateStr)) return -1;
        try {
            return LocalDate.parse(dateStr.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE).getMonthValue() - 1;
        } catch (DateTimeParseException e) {
            return -1;
        }
    }
}
