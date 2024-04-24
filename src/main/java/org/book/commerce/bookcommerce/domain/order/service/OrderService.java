package org.book.commerce.bookcommerce.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.book.commerce.bookcommerce.common.entity.ErrorCode;
import org.book.commerce.bookcommerce.common.exception.CommonException;
import org.book.commerce.bookcommerce.common.exception.ConflictException;
import org.book.commerce.bookcommerce.common.exception.NotAcceptException;
import org.book.commerce.bookcommerce.common.exception.NotFoundException;
import org.book.commerce.bookcommerce.domain.cart.domain.Cart;
import org.book.commerce.bookcommerce.domain.cart.repository.CartRepository;
import org.book.commerce.bookcommerce.domain.order.domain.Order;
import org.book.commerce.bookcommerce.domain.order.domain.OrderStatus;
import org.book.commerce.bookcommerce.domain.order.domain.ProductOrder;
import org.book.commerce.bookcommerce.domain.order.dto.OrderProductListDto;
import org.book.commerce.bookcommerce.domain.order.dto.OrderResultDto;
import org.book.commerce.bookcommerce.domain.order.dto.OrderlistDto;
import org.book.commerce.bookcommerce.domain.order.repository.OrderRepository;
import org.book.commerce.bookcommerce.domain.order.repository.ProductOrderRepository;
import org.book.commerce.bookcommerce.domain.product.domain.Product;
import org.book.commerce.bookcommerce.domain.product.repository.ProductRepository;
import org.book.commerce.bookcommerce.domain.user.domain.CustomUserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final ProductOrderRepository productOrderRepository;
    public OrderResultDto payOrder(CustomUserDetails customUserDetails) {
        String userId = customUserDetails.getUsername();
        List<Cart> cartList = cartRepository.findAllByUserEmail(userId);
        Order order = Order.builder().userEmail(userId).status(OrderStatus.ORDER_COMPLETE).build();
        Long orderId = orderRepository.save(order).getOrderId();
        for(Cart cart:cartList){
            // 장바구니는 지우고, 주문내역은 생성하고
            ProductOrder productOrder = ProductOrder.builder().productId(cart.getProductId())
                    .orderId(orderId).count(cart.getCount()).build();
            productOrderRepository.save(productOrder);
            Product product = productRepository.findById(cart.getProductId()).orElseThrow(()->new NotFoundException("요청한 물품을 찾을 수 없습니다. 문제 물품 번호: "+cart.getProductId()));
            int nowStock = product.getStock()-cart.getCount();
            if(nowStock<0) throw new ConflictException("주문하신 상품의 재고가 부족하여 구매를 할 수 없습니다. 확인해주세요. 상품 번호: "+product.getProductId());
            product.setStock(product.getStock()-cart.getCount());
            cartRepository.delete(cart);
            productRepository.save(product);
        }
        return new OrderResultDto(orderId);
    }


    public List<OrderlistDto> getOrderList(CustomUserDetails customUserDetails) {
        String userId = customUserDetails.getUsername();
        List<Order> orderList = orderRepository.findAllByUserEmail(userId);
        ArrayList<OrderlistDto> orderlistDtos = new ArrayList<>();
        for(Order order:orderList){
            List<ProductOrder> productOrderList = productOrderRepository.findAllByOrderId(order.getOrderId());
            List<OrderProductListDto> orderProductListDtos = new ArrayList<>();
            for(ProductOrder productOrder:productOrderList){
                Product product = productRepository.findById(productOrder.getProductId()).orElseThrow(()->new NotFoundException("요청한 물품을 찾을 수 없습니다. 문제 물품 번호: "+productOrder.getProductId()));
                orderProductListDtos.add(OrderProductListDto.builder().productName(product.getName())
                        .price(product.getPrice()).count(productOrder.getCount()).build());
            }
            orderlistDtos.add(OrderlistDto.builder().orderId(order.getOrderId())
                    .orderStatus(order.getStatus()).orderDate(order.getCreatedAt())
                    .orderProductList(orderProductListDtos).build());
        } // 이중 for문이라 좋지않음. 그리고 장바구니 - 물품으로 계속 호출해야해서 msa적용하기 쉽지않을거같음
        // 이중 for문을 없애고 호출을 줄이는 방법을 연구해봐야함
        // orderlist에는 간단하게 주문id와 주문날짜, 상태만 보여주고 상세로 들어가야지 주문 아이템 목록을 보여주는 것도 생각
        return orderlistDtos;
    }

    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(()->new NotFoundException("요청한 주문을 찾을 수 없습니다. 문제 주문 번호: "+orderId));
        if(order.getStatus()==OrderStatus.ORDER_COMPLETE){
            order.setStatus(OrderStatus.REQ_CANCEL);
            orderRepository.save(order);
        }
        else{
            throw new CommonException("배송중인 상품으로 주문 취소가 불가능합니다.",ErrorCode.BAD_REQUEST);
        }
    }

    public void refundOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(()->new NotFoundException("존재하지 않는 주문입니다."));
        if(order.getStatus()==OrderStatus.FINISH_SHIPPING){
            order.setStatus(OrderStatus.REQ_REFUND);
            orderRepository.save(order);
        }
        else{
            throw new NotAcceptException("반품이 불가능한 상태입니다.");
        }
    }

    @Transactional
    public void exceedOrderDay() {
        List<Order> orderlist = orderRepository.findAllOlderThanLast24HoursWithSpecificStatus(OrderStatus.ORDER_COMPLETE,OrderStatus.REQ_CANCEL,OrderStatus.SHIPPING,OrderStatus.REQ_REFUND);

        // 모든 주문에 대한 상태 업데이트 및 처리
        for (Order order : orderlist) {
            OrderStatus currentStatus = order.getStatus();
            if (currentStatus == OrderStatus.ORDER_COMPLETE ) {
                order.setStatus(OrderStatus.SHIPPING);
            } else if (currentStatus == OrderStatus.SHIPPING) {
                order.setStatus(OrderStatus.FINISH_SHIPPING);
            } else if (currentStatus == OrderStatus.REQ_REFUND) {
                order.setStatus(OrderStatus.FINISH_REFUND);
                returnStock(order);
            } else if (currentStatus == OrderStatus.REQ_CANCEL) {
                order.setStatus(OrderStatus.ORDER_CANCEL);
                returnStock(order);
            }
            orderRepository.save(order);
        }
    }

    private void returnStock(Order order){
        List<ProductOrder> productOrderList = productOrderRepository.findAllByOrderId(order.getOrderId());
        for(ProductOrder productOrder:productOrderList){
            Product product = productRepository.findById(productOrder.getProductId()).orElseThrow(()->new NotFoundException("상품을 찾을 수가 없습니다. 문제 물품 고유 번호: "+productOrder.getProductId()));
            product.setStock(product.getStock()+productOrder.getCount());
            productRepository.save(product);
        }
    }
}
