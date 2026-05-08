package com.buildledger.delivery.service;

import com.buildledger.delivery.dto.request.DeliveryRequestDTO;
import com.buildledger.delivery.dto.response.DeliveryResponseDTO;
import com.buildledger.delivery.enums.DeliveryStatus;
import java.util.List;

public interface DeliveryService {
    DeliveryResponseDTO createDelivery(DeliveryRequestDTO request);
    DeliveryResponseDTO getDeliveryById(Long deliveryId);
    List<DeliveryResponseDTO> getAllDeliveries();
    List<DeliveryResponseDTO> getDeliveriesByContract(Long contractId);
    List<DeliveryResponseDTO> getDeliveriesByStatus(DeliveryStatus status);
    DeliveryResponseDTO updateDeliveryStatus(Long deliveryId, DeliveryStatus nextStatus);
    DeliveryResponseDTO updateDelivery(Long deliveryId, DeliveryRequestDTO request);
    void deleteDelivery(Long deliveryId);
}

