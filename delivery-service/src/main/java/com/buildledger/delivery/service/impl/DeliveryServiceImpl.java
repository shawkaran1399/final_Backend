package com.buildledger.delivery.service.impl;
import com.buildledger.delivery.event.NotificationEvent;
import com.buildledger.delivery.event.NotificationProducer;
import com.buildledger.delivery.dto.request.DeliveryRequestDTO;
import com.buildledger.delivery.dto.response.ApiResponseDTO;
import com.buildledger.delivery.dto.response.DeliveryResponseDTO;
import com.buildledger.delivery.entity.Delivery;
import com.buildledger.delivery.enums.DeliveryStatus;
import com.buildledger.delivery.exception.BadRequestException;
import com.buildledger.delivery.exception.ResourceNotFoundException;
import com.buildledger.delivery.exception.ServiceUnavailableException;
import com.buildledger.delivery.feign.ContractServiceClient;
import com.buildledger.delivery.feign.ContractServiceFallback;
import com.buildledger.delivery.repository.DeliveryRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
class DeliveryServiceImpl implements com.buildledger.delivery.service.DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final ContractServiceClient contractServiceClient;
    private final NotificationProducer notificationProducer;  // ADD THIS

    public DeliveryResponseDTO createDelivery(DeliveryRequestDTO request) {
        log.info("Creating delivery for contract {}", request.getContractId());
        validateContractExists(request.getContractId());

        Delivery delivery = Delivery.builder()
            .contractId(request.getContractId())
            .date(request.getDate())
            .item(request.getItem())
            .quantity(request.getQuantity())
            .unit(request.getUnit())
            .remarks(request.getRemarks())
            .build();

        return mapToResponse(deliveryRepository.save(delivery));
    }

    @Transactional(readOnly = true)
    public DeliveryResponseDTO getDeliveryById(Long deliveryId) {
        return mapToResponse(findById(deliveryId));
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponseDTO> getAllDeliveries() {
        return deliveryRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponseDTO> getDeliveriesByContract(Long contractId) {
        validateContractExists(contractId);
        return deliveryRepository.findByContractId(contractId).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public DeliveryResponseDTO updateDeliveryStatus(Long deliveryId, DeliveryStatus nextStatus) {
        Delivery delivery = findById(deliveryId);
        DeliveryStatus current = delivery.getStatus();

        if (current == nextStatus) return mapToResponse(delivery);

        if (!current.canTransitionTo(nextStatus)) {
            throw new BadRequestException(
                "Invalid status transition from " + current + " to " + nextStatus +
                ". Allowed transitions: PENDING→MARKED_DELIVERED|DELAYED, MARKED_DELIVERED→ACCEPTED|REJECTED, DELAYED→MARKED_DELIVERED.");
        }

        // Role-based validation for specific transitions
        if (nextStatus == DeliveryStatus.ACCEPTED || nextStatus == DeliveryStatus.REJECTED) {
            requireRole("PROJECT_MANAGER", "ADMIN");
        }
        if (nextStatus == DeliveryStatus.MARKED_DELIVERED || nextStatus == DeliveryStatus.DELAYED) {
            requireRole("VENDOR", "ADMIN");
        }

        delivery.setStatus(nextStatus);
        DeliveryResponseDTO result = mapToResponse(deliveryRepository.save(delivery));

// ← NEW: Send notification for ACCEPTED or REJECTED
        if (nextStatus == DeliveryStatus.ACCEPTED) {
            notificationProducer.send("delivery-events", NotificationEvent.builder()
                    .recipientEmail("")
                    .recipientName("Vendor")
                    .type("DELIVERY_ACCEPTED")
                    .subject("Your delivery has been accepted")
                    .message("Your delivery #" + delivery.getDeliveryId() + " for item '" + delivery.getItem() + "' has been ACCEPTED by the project manager.")
                    .referenceId(String.valueOf(delivery.getDeliveryId()))
                    .referenceType("DELIVERY")
                    .build());
        } else if (nextStatus == DeliveryStatus.REJECTED) {
            notificationProducer.send("delivery-events", NotificationEvent.builder()
                    .recipientEmail("")
                    .recipientName("Vendor")
                    .type("DELIVERY_REJECTED")
                    .subject("Your delivery has been rejected")
                    .message("Your delivery #" + delivery.getDeliveryId() + " for item '" + delivery.getItem() + "' has been REJECTED. Please check with your project manager.")
                    .referenceId(String.valueOf(delivery.getDeliveryId()))
                    .referenceType("DELIVERY")
                    .build());
        }

        return result;
    }

    public DeliveryResponseDTO updateDelivery(Long deliveryId, DeliveryRequestDTO request) {
        Delivery delivery = findById(deliveryId);
        if (delivery.getStatus() != DeliveryStatus.PENDING) {
            throw new BadRequestException("Delivery details can only be updated when status is PENDING.");
        }
        if (request.getContractId() != null) {
            validateContractExists(request.getContractId());
            delivery.setContractId(request.getContractId());
        }
        if (request.getDate() != null) delivery.setDate(request.getDate());
        if (request.getItem() != null) delivery.setItem(request.getItem());
        if (request.getQuantity() != null) delivery.setQuantity(request.getQuantity());
        if (request.getUnit() != null) delivery.setUnit(request.getUnit());
        if (request.getRemarks() != null) delivery.setRemarks(request.getRemarks());
        return mapToResponse(deliveryRepository.save(delivery));
    }

    public void deleteDelivery(Long deliveryId) {
        Delivery delivery = findById(deliveryId);
        if (delivery.getStatus() != DeliveryStatus.PENDING) {
            throw new BadRequestException("Only PENDING deliveries can be deleted.");
        }
        deliveryRepository.delete(delivery);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateContractExists(Long contractId) {
        ApiResponseDTO<Map<String, Object>> response;
        try {
            response = contractServiceClient.getContractById(contractId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Contract", "id", contractId);
        } catch (FeignException e) {
            throw new ServiceUnavailableException("Contract Service is currently unavailable. Please try again later.");
        } catch (Exception e) {
            throw new ServiceUnavailableException("Contract Service is currently unavailable. Please try again later.");
        }

        if (ContractServiceFallback.MARKER.equals(response.getMessage())) {
            throw new ServiceUnavailableException("Contract Service is currently unavailable. Please try again later.");
        }
        if (!response.isSuccess() || response.getData() == null) {
            throw new ResourceNotFoundException("Contract", "id", contractId);
        }
    }

    private void requireRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
        boolean hasRole = auth.getAuthorities().stream()
            .anyMatch(a -> {
                for (String role : roles) {
                    if (a.getAuthority().equals("ROLE_" + role)) return true;
                }
                return false;
            });
        if (!hasRole) {
            throw new org.springframework.security.access.AccessDeniedException(
                "Access denied. Required roles: " + String.join(" or ", roles));
        }
    }

    private Delivery findById(Long id) {
        return deliveryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Delivery", "id", id));
    }

    private DeliveryResponseDTO mapToResponse(Delivery d) {
        return DeliveryResponseDTO.builder()
            .deliveryId(d.getDeliveryId()).contractId(d.getContractId())
            .date(d.getDate()).item(d.getItem()).quantity(d.getQuantity()).unit(d.getUnit())
            .remarks(d.getRemarks()).status(d.getStatus())
            .createdAt(d.getCreatedAt()).updatedAt(d.getUpdatedAt()).build();
    }
}

