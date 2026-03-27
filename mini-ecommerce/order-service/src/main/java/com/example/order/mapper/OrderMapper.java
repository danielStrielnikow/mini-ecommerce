package com.example.order.mapper;

import com.example.order.dto.response.OrderResponse;
import com.example.order.dto.response.OrderSummaryResponse;
import com.example.order.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderMapper {

    OrderResponse toResponse(Order order);

    OrderSummaryResponse toSummary(Order order);
}
