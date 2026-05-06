package com.buildledger.delivery.service;

import com.buildledger.delivery.dto.request.ServiceRequestDTO;
import com.buildledger.delivery.dto.response.ServiceResponseDTO;
import com.buildledger.delivery.enums.ServiceStatus;
import java.util.List;

public interface ServiceTrackingService {
    ServiceResponseDTO createService(ServiceRequestDTO request);
    ServiceResponseDTO getServiceById(Long serviceId);
    List<ServiceResponseDTO> getAllServices();
    List<ServiceResponseDTO> getServicesByContract(Long contractId);
    ServiceResponseDTO updateServiceStatus(Long serviceId, ServiceStatus nextStatus);
    ServiceResponseDTO updateService(Long serviceId, ServiceRequestDTO request);
    void deleteService(Long serviceId);
}

