package com.ccapp.shop.service;

import com.ccapp.admin.security.AdminPrincipal;
import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.commerce.domain.enums.CommerceMerchantStatus;
import com.ccapp.commerce.repository.CommerceMerchantRepository;
import com.ccapp.shop.domain.entity.ShopOrder;
import com.ccapp.shop.domain.enums.ShopOrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LegacyShopMutationSecurityService {

    private static final String SHOP_PRODUCTS_ENDPOINT = "/shop/products";
    private static final String SHOP_ORDER_CHECKOUT_ENDPOINT = "/shop/orders/checkout";
    private static final String SHOP_ORDER_STATUS_ENDPOINT = "/shop/orders/{orderId}/status";
    private static final String SHOP_ORDER_REFUND_ENDPOINT = "/shop/orders/{orderId}/refund";
    private static final String LEGACY_MUTABLE_ENDPOINTS_DISABLED_MESSAGE =
            "Legacy shop mutable endpoints are disabled";
    private static final Set<String> ADMIN_AUTHORITIES = Set.of(
            "ROLE_SUPER_ADMIN",
            "ROLE_COUNTRY_ADMIN",
            "ROLE_IMF_ADMIN",
            "ROLE_OPERATIONS",
            "ROLE_SUPPORT"
    );

    @Value("${store.legacy.mutable-endpoints.enabled:false}")
    private boolean legacyMutableEndpointsEnabled;

    private final ShopOrderService shopOrderService;
    private final CommerceMerchantRepository commerceMerchantRepository;

    public LegacyShopMutationSecurityService(ShopOrderService shopOrderService,
                                             CommerceMerchantRepository commerceMerchantRepository) {
        this.shopOrderService = shopOrderService;
        this.commerceMerchantRepository = commerceMerchantRepository;
    }

    public UUID authorizeProductCreate() {
        LegacyActor actor = resolveActor();
        ensureLegacyMutationsEnabled("CREATE_PRODUCT", SHOP_PRODUCTS_ENDPOINT, actor, null, null);
        if (actor.admin()) {
            deny(
                    "CREATE_PRODUCT",
                    SHOP_PRODUCTS_ENDPOINT,
                    actor,
                    null,
                    null,
                    "legacy product creation is restricted to active merchants; admins must use /api/backoffice/products"
            );
        }

        UUID merchantAccountHolderId = actor.requireAccountHolderId();
        boolean activeMerchant = commerceMerchantRepository.findByAccountHolderId(merchantAccountHolderId)
                .map(merchant -> merchant.getStatus() == CommerceMerchantStatus.ACTIVE)
                .orElse(false);
        if (!activeMerchant) {
            deny(
                    "CREATE_PRODUCT",
                    SHOP_PRODUCTS_ENDPOINT,
                    actor,
                    null,
                    null,
                    "legacy product creation is restricted to active merchants"
            );
        }

        logAuthorized("CREATE_PRODUCT", SHOP_PRODUCTS_ENDPOINT, actor.asType("MERCHANT"), null, null);
        return merchantAccountHolderId;
    }

    public UUID authorizeCheckout() {
        LegacyActor actor = resolveActor();
        ensureLegacyMutationsEnabled("CHECKOUT", SHOP_ORDER_CHECKOUT_ENDPOINT, actor, null, null);
        if (actor.admin()) {
            deny("CHECKOUT", SHOP_ORDER_CHECKOUT_ENDPOINT, actor, null, null,
                    "legacy checkout is restricted to the buyer account");
        }
        LegacyActor buyer = actor.asType("BUYER");
        logAuthorized("CHECKOUT", SHOP_ORDER_CHECKOUT_ENDPOINT, buyer, null, null);
        return buyer.requireAccountHolderId();
    }

    public UUID authorizeStatusUpdate(UUID orderId, ShopOrderStatus targetStatus) {
        LegacyActor actor = resolveActor();
        ensureLegacyMutationsEnabled("UPDATE_STATUS", SHOP_ORDER_STATUS_ENDPOINT, actor, orderId, targetStatus);

        ShopOrder order = shopOrderService.findOrder(orderId);
        if (actor.admin()) {
            if (targetStatus == ShopOrderStatus.PAID) {
                deny("UPDATE_STATUS", SHOP_ORDER_STATUS_ENDPOINT, actor, orderId, targetStatus,
                        "legacy payment status updates must be initiated by the buyer");
            }
            logAuthorized("UPDATE_STATUS", SHOP_ORDER_STATUS_ENDPOINT, actor, orderId, targetStatus);
            return actor.actorUserId();
        }

        UUID actorAccountHolderId = actor.requireAccountHolderId();
        if (actorAccountHolderId.equals(order.getBuyerId())) {
            LegacyActor buyer = actor.asType("BUYER");
            if (targetStatus == ShopOrderStatus.PAID || targetStatus == ShopOrderStatus.CANCELLED) {
                logAuthorized("UPDATE_STATUS", SHOP_ORDER_STATUS_ENDPOINT, buyer, orderId, targetStatus);
                return buyer.actorUserId();
            }
            deny("UPDATE_STATUS", SHOP_ORDER_STATUS_ENDPOINT, buyer, orderId, targetStatus,
                    "buyer can only pay or cancel their own legacy order");
        }

        if (order.getMerchantId() != null && order.getMerchantId().equals(actorAccountHolderId)) {
            LegacyActor merchant = actor.asType("MERCHANT");
            if (targetStatus == ShopOrderStatus.SHIPPED || targetStatus == ShopOrderStatus.DELIVERED) {
                logAuthorized("UPDATE_STATUS", SHOP_ORDER_STATUS_ENDPOINT, merchant, orderId, targetStatus);
                return merchant.actorUserId();
            }
            deny("UPDATE_STATUS", SHOP_ORDER_STATUS_ENDPOINT, merchant, orderId, targetStatus,
                    "merchant can only ship or deliver their own legacy order");
        }

        deny("UPDATE_STATUS", SHOP_ORDER_STATUS_ENDPOINT, actor, orderId, targetStatus,
                "actor is neither the buyer nor the merchant of this legacy order");
        return actor.actorUserId();
    }

    public void authorizeRefund(UUID orderId) {
        LegacyActor actor = resolveActor();
        ensureLegacyMutationsEnabled("REFUND", SHOP_ORDER_REFUND_ENDPOINT, actor, orderId, null);

        ShopOrder order = shopOrderService.findOrder(orderId);
        if (actor.admin()) {
            logAuthorized("REFUND", SHOP_ORDER_REFUND_ENDPOINT, actor, orderId, null);
            return;
        }

        UUID actorAccountHolderId = actor.requireAccountHolderId();
        if (order.getMerchantId() != null && order.getMerchantId().equals(actorAccountHolderId)) {
            logAuthorized("REFUND", SHOP_ORDER_REFUND_ENDPOINT, actor.asType("MERCHANT"), orderId, null);
            return;
        }

        deny("REFUND", SHOP_ORDER_REFUND_ENDPOINT, actor.asType("BUYER"), orderId, null,
                "refund is restricted to the merchant owner or an admin on legacy shop endpoints");
    }

    private void ensureLegacyMutationsEnabled(String action,
                                              String endpoint,
                                              LegacyActor actor,
                                              UUID orderId,
                                              ShopOrderStatus targetStatus) {
        if (legacyMutableEndpointsEnabled) {
            return;
        }
        log.warn(
                "legacy_shop_mutation_blocked action={} endpoint={} reason=disabled actorType={} actorUserId={} accountHolderId={} orderId={} targetStatus={}",
                action,
                endpoint,
                actor.actorType(),
                actor.actorUserId(),
                actor.accountHolderId(),
                orderId,
                targetStatus
        );
        throw new AccessDeniedException(LEGACY_MUTABLE_ENDPOINTS_DISABLED_MESSAGE);
    }

    private void logAuthorized(String action,
                               String endpoint,
                               LegacyActor actor,
                               UUID orderId,
                               ShopOrderStatus targetStatus) {
        log.info(
                "legacy_shop_mutation_authorized action={} endpoint={} actorType={} actorUserId={} accountHolderId={} orderId={} targetStatus={}",
                action,
                endpoint,
                actor.actorType(),
                actor.actorUserId(),
                actor.accountHolderId(),
                orderId,
                targetStatus
        );
    }

    private void deny(String action,
                      String endpoint,
                      LegacyActor actor,
                      UUID orderId,
                      ShopOrderStatus targetStatus,
                      String reason) {
        log.warn(
                "legacy_shop_mutation_denied action={} endpoint={} reason={} actorType={} actorUserId={} accountHolderId={} orderId={} targetStatus={} authorities={}",
                action,
                endpoint,
                reason,
                actor.actorType(),
                actor.actorUserId(),
                actor.accountHolderId(),
                orderId,
                targetStatus,
                actor.authorities()
        );
        throw new AccessDeniedException(reason);
    }

    private LegacyActor resolveActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new UnauthorizedException("Authentication required");
        }

        Set<String> authorities = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toUnmodifiableSet());
        Object principal = authentication.getPrincipal();

        if (principal instanceof AuthenticatedPrincipal mobilePrincipal) {
            UUID accountHolderId = mobilePrincipal.accountHolderId() != null
                    ? mobilePrincipal.accountHolderId()
                    : mobilePrincipal.userId();
            if (accountHolderId == null) {
                throw new UnauthorizedException("Account holder context missing");
            }
            UUID actorUserId = mobilePrincipal.userId() != null ? mobilePrincipal.userId() : accountHolderId;
            return new LegacyActor(actorUserId, accountHolderId, "ACCOUNT_HOLDER", false, authorities);
        }

        if (principal instanceof AdminPrincipal adminPrincipal) {
            return new LegacyActor(
                    adminPrincipal.adminUserId(),
                    null,
                    "ADMIN_" + adminPrincipal.role().name(),
                    true,
                    authorities
            );
        }

        if (hasAdminAuthority(authorities)) {
            return new LegacyActor(null, null, "ADMIN", true, authorities);
        }

        throw new UnauthorizedException("Authentication required");
    }

    private boolean hasAdminAuthority(Set<String> authorities) {
        return authorities.stream().anyMatch(ADMIN_AUTHORITIES::contains);
    }

    private record LegacyActor(UUID actorUserId,
                               UUID accountHolderId,
                               String actorType,
                               boolean admin,
                               Set<String> authorities) {

        private LegacyActor asType(String newActorType) {
            return new LegacyActor(actorUserId, accountHolderId, newActorType, admin, authorities);
        }

        private UUID requireAccountHolderId() {
            if (accountHolderId == null) {
                throw new UnauthorizedException("Account holder context missing");
            }
            return accountHolderId;
        }
    }
}
