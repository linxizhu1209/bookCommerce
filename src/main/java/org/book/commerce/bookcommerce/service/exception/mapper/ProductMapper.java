package org.book.commerce.bookcommerce.service.exception.mapper;


import org.book.commerce.bookcommerce.controller.dto.AllProductList;
import org.book.commerce.bookcommerce.repository.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ProductMapper {
    ProductMapper INSTANCE = Mappers.getMapper(ProductMapper.class);

    @Mapping(target="productId",source="productId")
    @Mapping(target="price",source="price")
    @Mapping(target="name",source="name")
    @Mapping(target="imgUrl",source="thumbnailUrl")
    @Mapping(target = "imgName",source = "thumbnailName")
    AllProductList ProductEntityToDto(Product product);

}
