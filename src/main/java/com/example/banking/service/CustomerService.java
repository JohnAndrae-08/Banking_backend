package com.example.banking.service;

import com.example.banking.dto.CustomerResponse;
import com.example.banking.dto.UpdateCustomerRequest;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.model.Customer;
import com.example.banking.monitor.FlowPublisher;
import com.example.banking.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final FlowPublisher flow;

    public CustomerService(CustomerRepository customers, FlowPublisher flow) {
        this.customers = customers;
        this.flow = flow;
    }

    public CustomerResponse getMyProfile(Long userId) {
        flow.service("CustomerService.getMyProfile started");        Customer c = customers.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
        return toResponse(c);
    }

    @Transactional
    public CustomerResponse updateMyProfile(Long userId, UpdateCustomerRequest req) {
        Customer c = customers.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
        if (req.getFirstName() != null) c.setFirstName(req.getFirstName());
        if (req.getMiddleName() != null) c.setMiddleName(req.getMiddleName());
        if (req.getLastName() != null) c.setLastName(req.getLastName());
        if (req.getPhone() != null) c.setPhone(req.getPhone());
        if (req.getAddress() != null) c.setAddress(req.getAddress());
        customers.update(c);
        return toResponse(c);
    }

    public Customer requireCustomer(Long userId) {
        return customers.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
    }

    private CustomerResponse toResponse(Customer c) {
        CustomerResponse r = new CustomerResponse();
        r.setId(c.getId());
        r.setFirstName(c.getFirstName());
        r.setMiddleName(c.getMiddleName());
        r.setLastName(c.getLastName());
        r.setPhone(c.getPhone());
        r.setAddress(c.getAddress());
        r.setCreatedAt(c.getCreatedAt());
        return r;
    }
}
